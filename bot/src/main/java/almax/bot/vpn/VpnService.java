package almax.bot.vpn;

import almax.bot.config.BotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class VpnService {

    public enum Action { ADDED, ROTATED }

    public record Provision(String alias, String vlessUri, byte[] qrPng, Action action) {}

    // Pinned to the value of `container_name:` in russia/docker-compose.yml.
    // The russia stack always names the entry-hop container exactly this.
    private static final String DOCKER_BINARY = "docker";
    private static final String CONTAINER_NAME = "vpn-russia";

    private static final Pattern VLESS_LINE = Pattern.compile("(?m)^vless://\\S+");
    private static final Pattern ALIAS_VALID = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private final BotProperties props;

    public Provision approve(String alias) {
        validateAlias(alias);
        boolean exists = listAliases().contains(alias);
        Action action = exists ? Action.ROTATED : Action.ADDED;
        String sub = exists ? "rotate" : "add";
        String stdout = runVpn(List.of(sub, alias));
        String uri = extractVlessUri(stdout)
                .orElseThrow(() -> new VpnException(
                        "vpn " + sub + " " + alias + " produced no vless:// line. Output:\n" + stdout));
        byte[] qr = renderQrPng(uri);
        return new Provision(alias, uri, qr, action);
    }

    public boolean removeIfExists(String alias) {
        if (alias == null || alias.isBlank()) return false;
        if (!ALIAS_VALID.matcher(alias).matches()) {
            log.warn("Skipping vpn remove — alias '{}' has unexpected characters", alias);
            return false;
        }
        if (!listAliases().contains(alias)) {
            log.info("Skipping vpn remove — no VPN user with alias '{}'", alias);
            return false;
        }
        runVpn(List.of("remove", alias));
        return true;
    }

    public List<String> listAliases() {
        String out = runVpn(List.of("list"));
        List<String> aliases = new ArrayList<>();
        for (String raw : out.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            if (line.startsWith("(")) continue;
            if (line.startsWith("NAME ") || line.startsWith("---")) continue;
            String first = line.split("\\s+", 2)[0];
            if (ALIAS_VALID.matcher(first).matches()) {
                aliases.add(first);
            }
        }
        return aliases;
    }

    private byte[] renderQrPng(String uri) {
        long timeoutMs = props.vpn().commandTimeoutMs();
        ProcessBuilder pb = new ProcessBuilder(
                DOCKER_BINARY, "exec", "-i", CONTAINER_NAME,
                "qrencode", "-t", "PNG", "-o", "-");
        pb.redirectErrorStream(false);
        try {
            Process p = pb.start();
            p.getOutputStream().write(uri.getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().close();
            byte[] png = p.getInputStream().readAllBytes();
            byte[] err = p.getErrorStream().readAllBytes();
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                throw new VpnException("qrencode timed out after " + timeoutMs + "ms");
            }
            if (p.exitValue() != 0 || png.length == 0) {
                throw new VpnException("qrencode failed (exit=" + p.exitValue() + "): "
                        + new String(err, StandardCharsets.UTF_8));
            }
            return png;
        } catch (IOException e) {
            throw new VpnException("qrencode invocation failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VpnException("Interrupted while waiting for qrencode");
        }
    }

    private String runVpn(List<String> args) {
        long timeoutMs = props.vpn().commandTimeoutMs();
        List<String> cmd = new ArrayList<>(args.size() + 4);
        cmd.add(DOCKER_BINARY);
        cmd.add("exec");
        cmd.add(CONTAINER_NAME);
        cmd.add("vpn");
        cmd.addAll(args);

        log.info("Running: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            p.getOutputStream().close();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                throw new VpnException("`vpn " + String.join(" ", args)
                        + "` timed out after " + timeoutMs + "ms");
            }
            String stdout = stripAnsi(new String(out, StandardCharsets.UTF_8));
            if (p.exitValue() != 0) {
                throw new VpnException("`vpn " + String.join(" ", args)
                        + "` failed (exit=" + p.exitValue() + "):\n" + stdout);
            }
            return stdout;
        } catch (IOException e) {
            throw new VpnException("Failed to invoke `vpn " + String.join(" ", args)
                    + "`: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VpnException("Interrupted while waiting for `vpn " + String.join(" ", args) + "`");
        }
    }

    static java.util.Optional<String> extractVlessUri(String text) {
        String stripped = stripAnsi(text);
        Matcher m = VLESS_LINE.matcher(stripped);
        String last = null;
        while (m.find()) last = m.group();
        return java.util.Optional.ofNullable(last);
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\\[[;0-9]*[a-zA-Z]", "");
    }

    private static void validateAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Alias is required");
        }
        if (!ALIAS_VALID.matcher(alias).matches()) {
            throw new IllegalArgumentException(
                    "Invalid alias '" + alias + "' (allowed: letters, digits, _ . -)");
        }
    }

}

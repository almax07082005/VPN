package almax.bot.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository repo;

    public record RegisterResult(BotUser user, RegisterOutcome outcome) {}

    @Transactional
    public RegisterResult register(long tgUserId, String username) {
        Optional<BotUser> existing = repo.findByTgUserId(tgUserId);
        if (existing.isPresent()) {
            BotUser u = existing.get();
            if (username != null && !username.equals(u.getUsername())) {
                u.setUsername(username);
            }
            RegisterOutcome outcome = switch (u.getStatus()) {
                case PENDING -> RegisterOutcome.ALREADY_PENDING;
                case APPROVED -> RegisterOutcome.ALREADY_APPROVED;
                case DENIED -> RegisterOutcome.DENIED;
            };
            return new RegisterResult(u, outcome);
        }
        BotUser fresh = BotUser.builder()
                .tgUserId(tgUserId)
                .username(username)
                .status(UserStatus.PENDING)
                .build();
        BotUser saved = repo.save(fresh);
        log.info("Registered new user: id={} tg={} @{}", saved.getId(), saved.getTgUserId(), saved.getUsername());
        return new RegisterResult(saved, RegisterOutcome.NEW_PENDING);
    }

    @Transactional
    public BotUser approve(long localId, String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Alias is required");
        }
        BotUser user = require(localId);
        user.setAlias(alias.trim());
        user.setStatus(UserStatus.APPROVED);
        log.info("Approved user id={} tg={} alias={}", user.getId(), user.getTgUserId(), user.getAlias());
        return user;
    }

    @Transactional
    public BotUser remove(long localId) {
        BotUser user = require(localId);
        repo.delete(user);
        log.info("Removed user id={} tg={}", user.getId(), user.getTgUserId());
        return user;
    }

    @Transactional
    public BotUser deny(long localId) {
        BotUser user = require(localId);
        user.setStatus(UserStatus.DENIED);
        log.info("Denied user id={} tg={}", user.getId(), user.getTgUserId());
        return user;
    }

    @Transactional(readOnly = true)
    public List<BotUser> listAll() {
        return repo.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<BotUser> listApproved() {
        return repo.findAllByStatus(UserStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public List<BotUser> findByIds(List<Long> localIds) {
        if (localIds == null || localIds.isEmpty()) return List.of();
        return repo.findAllByIdInOrderByIdAsc(localIds);
    }

    @Transactional(readOnly = true)
    public BotUser findRequired(long localId) {
        return require(localId);
    }

    private BotUser require(long localId) {
        return repo.findById(localId)
                .orElseThrow(() -> new NoSuchElementException("User #" + localId + " not found"));
    }
}

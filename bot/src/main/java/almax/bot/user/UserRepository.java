package almax.bot.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<BotUser, Long> {

    Optional<BotUser> findByTgUserId(Long tgUserId);

    List<BotUser> findAllByStatus(UserStatus status);

    List<BotUser> findAllByOrderByIdAsc();

    List<BotUser> findAllByIdInOrderByIdAsc(Collection<Long> ids);
}

package com.zhangzhewen.ragdemo.application.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhangzhewen.ragdemo.application.BusinessException;
import com.zhangzhewen.ragdemo.application.dto.UserDtos;
import com.zhangzhewen.ragdemo.domain.gateway.AvatarStorageGateway;
import com.zhangzhewen.ragdemo.domain.gateway.UserGateway;
import com.zhangzhewen.ragdemo.domain.identity.Role;
import com.zhangzhewen.ragdemo.domain.identity.User;
import com.zhangzhewen.ragdemo.domain.identity.UserStatus;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserPasswordPolicyTest {

  // Regression: ISSUE-002 — 空密码被静默替换为可预测的默认密码 123456
  // Found by /qa on 2026-07-23
  // Report: /tmp/rag-demo-qa-reports/qa-report-localhost-2026-07-23.md
  @Test
  void createRejectsBlankPasswordInsteadOfUsingDefault() {
    TestUserGateway users = new TestUserGateway();
    UserService service = new UserService(users, new TestPasswordEncoder(), new TestAvatarStorage());
    var request = new UserDtos.CreateRequest("new-user", "新用户", " ", null, "ENABLED",
        Set.of("USER"));

    assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class);
  }

  @Test
  void resetRejectsBlankPasswordInsteadOfUsingDefault() {
    TestUserGateway users = new TestUserGateway();
    UserService service = new UserService(users, new TestPasswordEncoder(), new TestAvatarStorage());

    assertThatThrownBy(() -> service.reset(1L, " ")).isInstanceOf(BusinessException.class);
  }

  private static final class TestPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
      return "encoded:" + rawPassword;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
      return encode(rawPassword).equals(encodedPassword);
    }
  }

  private static final class TestAvatarStorage implements AvatarStorageGateway {

    @Override
    public String save(String extension, InputStream inputStream) {
      throw new UnsupportedOperationException();
    }

    @Override
    public StoredAvatar load(String fileName) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class TestUserGateway implements UserGateway {

    @Override
    public Optional<User> findByUsername(String username) {
      return Optional.empty();
    }

    @Override
    public Optional<User> findUserById(Long id) {
      return Optional.empty();
    }

    @Override
    public List<User> list() {
      return List.of();
    }

    @Override
    public Long create(String username, String password, String nickname, String avatarUrl,
        UserStatus status, Set<Role> roles) {
      return 1L;
    }

    @Override
    public void update(Long id, String nickname, String avatarUrl, UserStatus status,
        Set<Role> roles) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void resetPassword(Long id, String encodedPassword) {
    }

    @Override
    public void touchLastLogin(Long id) {
      throw new UnsupportedOperationException();
    }
  }
}

import 'package:sroadtutor/features/auth/domain/entities/auth_session.dart';
import 'package:sroadtutor/features/auth/domain/entities/auth_tokens.dart';
import 'package:sroadtutor/features/auth/domain/entities/user.dart';
import 'package:sroadtutor/features/auth/domain/entities/user_role.dart';
import 'package:sroadtutor/features/auth/domain/repositories/auth_repository.dart';

/// Minimal in-memory AuthRepository for widget + use-case tests.
/// Keeps tests synchronous-ish and avoids needing mocktail for simple flows.
class FakeAuthRepository implements AuthRepository {
  FakeAuthRepository({this.throwOnLogin = false, this.cached});

  bool throwOnLogin;
  User? cached;

  final List<String> calls = [];

  AuthSession _sessionFor(String email, {UserRole role = UserRole.student}) {
    final user = User(
      id: 'test-user-id',
      email: email,
      fullName: 'Test User',
      role: role,
    );
    cached = user;
    return AuthSession(
      user: user,
      tokens: const AuthTokens(
        accessToken: 'access-token-xyz',
        refreshToken: 'refresh-token-xyz',
      ),
    );
  }

  @override
  Future<User?> getCachedUser() async {
    calls.add('getCachedUser');
    return cached;
  }

  @override
  Future<AuthSession> loginWithEmail({
    required String email,
    required String password,
  }) async {
    calls.add('loginWithEmail:$email');
    if (throwOnLogin) throw StateError('bad creds');
    return _sessionFor(email);
  }

  @override
  Future<AuthSession> signup({
    required String email,
    required String password,
    required String fullName,
    required UserRole role,
    String? phoneNumber,
  }) async {
    calls.add('signup:$email:${role.wireName}');
    return _sessionFor(email, role: role);
  }

  @override
  Future<AuthSession> loginWithGoogle({
    required String idToken,
    UserRole? role,
  }) async {
    calls.add('google:$idToken');
    return _sessionFor('google@example.com', role: role ?? UserRole.student);
  }

  @override
  Future<AuthSession> loginWithFacebook({
    required String accessToken,
    UserRole? role,
  }) async {
    calls.add('facebook:$accessToken');
    return _sessionFor('fb@example.com', role: role ?? UserRole.student);
  }

  @override
  Future<AuthSession> refresh() async {
    calls.add('refresh');
    final u = cached;
    if (u == null) throw StateError('no session');
    return AuthSession(
      user: u,
      tokens: const AuthTokens(
        accessToken: 'new-access',
        refreshToken: 'new-refresh',
      ),
    );
  }

  @override
  Future<void> logout() async {
    calls.add('logout');
    cached = null;
  }
}

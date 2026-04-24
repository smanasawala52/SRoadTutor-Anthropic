import '../entities/auth_session.dart';
import '../entities/user.dart';
import '../entities/user_role.dart';

/// Domain-facing contract for everything auth-related.
///
/// The *implementation* lives in data/repositories/auth_repository_impl.dart
/// and knows about Dio, JSON, secure storage. The *use cases* below depend
/// only on this abstract interface — which means we can unit-test them with
/// a hand-rolled fake in seconds.
abstract class AuthRepository {
  /// Returns the cached user (from secure storage + Drift) if we already have
  /// valid tokens on disk. Returns `null` if no session is persisted.
  Future<User?> getCachedUser();

  /// Pure email+password login.
  Future<AuthSession> loginWithEmail({
    required String email,
    required String password,
  });

  /// Create a new local account. First OAuth login for an email *must* carry
  /// a role; subsequent logins never touch it.
  Future<AuthSession> signup({
    required String email,
    required String password,
    required String fullName,
    required UserRole role,
    String? phoneNumber,
  });

  /// Verifies a Google ID token with the backend. [role] is required only if
  /// this is the first time this email is signing in.
  Future<AuthSession> loginWithGoogle({
    required String idToken,
    UserRole? role,
  });

  /// Verifies a Facebook access token with the backend. See [loginWithGoogle]
  /// for the [role] contract.
  Future<AuthSession> loginWithFacebook({
    required String accessToken,
    UserRole? role,
  });

  /// Exchange refresh token → new token pair. Never called directly from UI;
  /// the Dio interceptor handles it. Exposed here so it's testable.
  Future<AuthSession> refresh();

  /// Invalidates the refresh token server-side (best-effort) and wipes all
  /// local auth state regardless.
  Future<void> logout();
}

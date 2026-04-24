import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../main_common.dart';
import '../../../../core/network/dio_client.dart';
import '../../data/datasources/auth_local_datasource.dart';
import '../../data/datasources/auth_remote_datasource.dart';
import '../../data/repositories/auth_repository_impl.dart';
import '../../domain/entities/auth_session.dart';
import '../../domain/entities/user.dart';
import '../../domain/entities/user_role.dart';
import '../../domain/repositories/auth_repository.dart';
import '../../domain/usecases/facebook_login_usecase.dart';
import '../../domain/usecases/google_login_usecase.dart';
import '../../domain/usecases/login_with_email_usecase.dart';
import '../../domain/usecases/logout_usecase.dart';
import '../../domain/usecases/signup_usecase.dart';

/// ─── High-level auth state machine ───────────────────────────────────────
/// Split into three discrete values so the router can drive navigation with a
/// simple switch and tests can assert transitions.
enum AuthStatus {
  unauthenticated,
  authenticated,

  /// OAuth succeeded server-side but no role was assigned yet — user needs
  /// to pick one before we can proceed into the app.
  needsRoleSelection,
}

/// ─── Infrastructure providers (Dio, datasources, repo) ───────────────────

final dioProvider = Provider((ref) {
  final env = ref.watch(envConfigProvider);
  final storage = ref.watch(secureStorageProvider);
  final logger = ref.watch(appLoggerProvider);
  return DioClientBuilder(
    baseUrl: env.apiBaseUrl,
    secureStorage: storage,
    logger: logger,
    enableDebugLogs: env.enableDebugLogs,
    onSessionExpired: () async {
      // Force controllers to re-read: they'll see "unauthenticated" now.
      ref.invalidate(authStateProvider);
      ref.invalidate(currentUserProvider);
    },
  ).build();
});

final authRemoteDataSourceProvider = Provider((ref) =>
    AuthRemoteDataSource(ref.watch(dioProvider)));

final authLocalDataSourceProvider = Provider((ref) => AuthLocalDataSource(
      secureStorage: ref.watch(secureStorageProvider),
      db: ref.watch(localDatabaseProvider),
    ));

final authRepositoryProvider = Provider<AuthRepository>((ref) =>
    AuthRepositoryImpl(
      remote: ref.watch(authRemoteDataSourceProvider),
      local: ref.watch(authLocalDataSourceProvider),
    ));

/// ─── Use-case providers ──────────────────────────────────────────────────

final loginWithEmailUseCaseProvider = Provider(
    (ref) => LoginWithEmailUseCase(ref.watch(authRepositoryProvider)));
final signupUseCaseProvider =
    Provider((ref) => SignupUseCase(ref.watch(authRepositoryProvider)));
final googleLoginUseCaseProvider = Provider(
    (ref) => GoogleLoginUseCase(ref.watch(authRepositoryProvider)));
final facebookLoginUseCaseProvider = Provider(
    (ref) => FacebookLoginUseCase(ref.watch(authRepositoryProvider)));
final logoutUseCaseProvider =
    Provider((ref) => LogoutUseCase(ref.watch(authRepositoryProvider)));

/// ─── Reactive auth state ─────────────────────────────────────────────────

/// Emits the app's current auth status. The router listens to this.
///
/// We expose it as a FutureProvider so the splash screen can naturally
/// render a spinner while we check disk for persisted tokens.
final authStateProvider = FutureProvider<AuthStatus>((ref) async {
  final repo = ref.watch(authRepositoryProvider);
  final user = await repo.getCachedUser();
  if (user == null) return AuthStatus.unauthenticated;
  return AuthStatus.authenticated;
});

/// Current user when we have one. `null` while logged out.
final currentUserProvider = FutureProvider<User?>((ref) async {
  final repo = ref.watch(authRepositoryProvider);
  return repo.getCachedUser();
});

/// Controller that mutates auth state. Screens call `ref.read(authController
/// .notifier).loginWithEmail(...)` and watch the controller for pending/
/// error UI state.
class AuthController extends AsyncNotifier<AuthSession?> {
  @override
  Future<AuthSession?> build() async => null;

  Future<void> loginWithEmail(String email, String password) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final session = await ref
          .read(loginWithEmailUseCaseProvider)
          .call(email: email, password: password);
      ref.invalidate(authStateProvider);
      ref.invalidate(currentUserProvider);
      return session;
    });
  }

  Future<void> signup({
    required String email,
    required String password,
    required String fullName,
    required UserRole role,
    String? phoneNumber,
  }) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final session = await ref.read(signupUseCaseProvider).call(
            email: email,
            password: password,
            fullName: fullName,
            role: role,
            phoneNumber: phoneNumber,
          );
      ref.invalidate(authStateProvider);
      ref.invalidate(currentUserProvider);
      return session;
    });
  }

  Future<void> loginWithGoogle({
    required String idToken,
    UserRole? role,
  }) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final session = await ref
          .read(googleLoginUseCaseProvider)
          .call(idToken: idToken, role: role);
      ref.invalidate(authStateProvider);
      ref.invalidate(currentUserProvider);
      return session;
    });
  }

  Future<void> loginWithFacebook({
    required String accessToken,
    UserRole? role,
  }) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final session = await ref
          .read(facebookLoginUseCaseProvider)
          .call(accessToken: accessToken, role: role);
      ref.invalidate(authStateProvider);
      ref.invalidate(currentUserProvider);
      return session;
    });
  }

  Future<void> logout() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      await ref.read(logoutUseCaseProvider).call();
      ref.invalidate(authStateProvider);
      ref.invalidate(currentUserProvider);
      return null;
    });
  }
}

final authControllerProvider =
    AsyncNotifierProvider<AuthController, AuthSession?>(AuthController.new);

import 'package:drift/drift.dart';

import '../../../../core/storage/local_database.dart';
import '../../../../core/storage/secure_storage_service.dart';
import '../../domain/entities/auth_tokens.dart';
import '../../domain/entities/user.dart';
import '../../domain/entities/user_role.dart';

/// Handles every on-device persistence concern for auth:
///   * JWTs → Keychain / EncryptedSharedPreferences
///   * cached user profile → Drift (so we can boot offline)
class AuthLocalDataSource {
  AuthLocalDataSource({
    required this.secureStorage,
    required this.db,
  });

  final SecureStorageService secureStorage;
  final LocalDatabase db;

  // ─── Tokens ───────────────────────────────────────────────────────────
  Future<void> saveTokens(AuthTokens tokens) async {
    await secureStorage.writeAccessToken(tokens.accessToken);
    await secureStorage.writeRefreshToken(tokens.refreshToken);
  }

  Future<AuthTokens?> readTokens() async {
    final access = await secureStorage.readAccessToken();
    final refresh = await secureStorage.readRefreshToken();
    if (access == null || refresh == null) return null;
    return AuthTokens(accessToken: access, refreshToken: refresh);
  }

  Future<void> clearTokens() => secureStorage.clear();

  // ─── Cached user ─────────────────────────────────────────────────────
  Future<void> cacheUser(User user) async {
    await secureStorage.writeUserId(user.id);
    await db.upsertCachedUser(
      CachedUsersCompanion(
        id: Value(user.id),
        email: Value(user.email),
        fullName: Value(user.fullName),
        role: Value(user.role.wireName),
        schoolId: Value(user.schoolId),
        avatarUrl: Value(user.avatarUrl),
        updatedAt: Value(DateTime.now().toUtc()),
      ),
    );
  }

  Future<User?> readCachedUser() async {
    final id = await secureStorage.readUserId();
    if (id == null) return null;
    final row = await db.getCachedUser(id);
    if (row == null) return null;
    return User(
      id: row.id,
      email: row.email,
      fullName: row.fullName,
      role: UserRole.fromWire(row.role),
      schoolId: row.schoolId,
      avatarUrl: row.avatarUrl,
    );
  }

  Future<void> clearCachedUser() async {
    final id = await secureStorage.readUserId();
    if (id != null) {
      await db.clearCachedUser(id);
    }
  }
}

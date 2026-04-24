import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqlite3_flutter_libs/sqlite3_flutter_libs.dart';

part 'local_database.g.dart';

/// ════════════════════════════════════════════════════════════════════════════
///  SRoadTutor — Offline-first SQLite store
/// ════════════════════════════════════════════════════════════════════════════
/// Drift generates a type-safe Dart API from the table definitions below.
/// After editing this file, run:
///
///   dart run build_runner build --delete-conflicting-outputs
///
/// The generated `local_database.g.dart` is committed-out in `.gitignore`, so
/// each developer regenerates it locally. CI runs the same step before tests.
/// ════════════════════════════════════════════════════════════════════════════

/// Cached copy of the logged-in user so we can boot to the home screen
/// without a round-trip to the backend.
class CachedUsers extends Table {
  TextColumn get id => text()();
  TextColumn get email => text()();
  TextColumn get fullName => text()();
  TextColumn get role => text()();
  TextColumn get schoolId => text().nullable()();
  TextColumn get avatarUrl => text().nullable()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {id};
}

/// A queue of API calls we wanted to make while offline.
/// When connectivity returns, `SyncEngine` drains this table in FIFO order.
class PendingOps extends Table {
  IntColumn get id => integer().autoIncrement()();

  /// e.g. 'session.create', 'payment.update', 'mistake.log'.
  TextColumn get opType => text()();

  /// Serialized JSON payload for the op.
  TextColumn get payloadJson => text()();

  /// HTTP method + path to replay against the backend.
  TextColumn get method => text()();
  TextColumn get path => text()();

  IntColumn get retryCount => integer().withDefault(const Constant(0))();
  TextColumn get lastError => text().nullable()();

  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get nextAttemptAt => dateTime()();
}

/// Arbitrary small key-value scratchpad (last-synced timestamps, feature
/// flags, UI prefs, etc.). Keeps us from having to add a new table every
/// time we need to persist one field.
class KeyValueEntries extends Table {
  TextColumn get key => text()();
  TextColumn get valueJson => text()();
  DateTimeColumn get updatedAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => {key};
}

@DriftDatabase(tables: [CachedUsers, PendingOps, KeyValueEntries])
class LocalDatabase extends _$LocalDatabase {
  LocalDatabase() : super(_openConnection());

  /// Visible for tests: `LocalDatabase.forTesting(NativeDatabase.memory())`.
  LocalDatabase.forTesting(super.executor);

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) => m.createAll(),
        onUpgrade: (m, from, to) async {
          // Future migrations go here. For schemaVersion = 1 nothing to do.
        },
        beforeOpen: (details) async {
          // Enforce foreign keys even though we don't use them yet — cheap
          // insurance for future schema additions.
          await customStatement('PRAGMA foreign_keys = ON;');
        },
      );

  // ─── Typed accessors used across the app ──────────────────────────────

  Future<CachedUser?> getCachedUser(String userId) =>
      (select(cachedUsers)..where((u) => u.id.equals(userId)))
          .getSingleOrNull();

  Future<void> upsertCachedUser(CachedUsersCompanion entry) =>
      into(cachedUsers).insertOnConflictUpdate(entry);

  Future<void> clearCachedUser(String userId) =>
      (delete(cachedUsers)..where((u) => u.id.equals(userId))).go();

  Future<int> enqueuePendingOp(PendingOpsCompanion entry) =>
      into(pendingOps).insert(entry);

  Future<List<PendingOp>> pendingOpsReadyNow(DateTime now) =>
      (select(pendingOps)
            ..where((p) => p.nextAttemptAt.isSmallerOrEqualValue(now))
            ..orderBy([(p) => OrderingTerm.asc(p.id)]))
          .get();

  Future<void> deletePendingOp(int id) =>
      (delete(pendingOps)..where((p) => p.id.equals(id))).go();
}

LazyDatabase _openConnection() {
  return LazyDatabase(() async {
    // Make sure sqlite3 native libs are ready before we open the DB file.
    if (Platform.isAndroid) {
      await applyWorkaroundToOpenSqlite3OnOldAndroidVersions();
    }
    final dir = await getApplicationDocumentsDirectory();
    final file = File(p.join(dir.path, 'sroadtutor.sqlite'));
    return NativeDatabase.createInBackground(file);
  });
}

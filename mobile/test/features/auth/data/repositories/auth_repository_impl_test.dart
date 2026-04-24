import 'package:dio/dio.dart';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:sroadtutor/core/errors/failure.dart';
import 'package:sroadtutor/core/storage/local_database.dart';
import 'package:sroadtutor/core/storage/secure_storage_service.dart';
import 'package:sroadtutor/features/auth/data/datasources/auth_local_datasource.dart';
import 'package:sroadtutor/features/auth/data/datasources/auth_remote_datasource.dart';
import 'package:sroadtutor/features/auth/data/models/auth_response_model.dart';
import 'package:sroadtutor/features/auth/data/models/user_model.dart';
import 'package:sroadtutor/features/auth/data/repositories/auth_repository_impl.dart';
import 'package:sroadtutor/features/auth/domain/entities/user_role.dart';

class _MockRemote extends Mock implements AuthRemoteDataSource {}

void main() {
  late LocalDatabase db;
  late InMemorySecureStorageService storage;
  late AuthLocalDataSource local;
  late _MockRemote remote;
  late AuthRepositoryImpl repo;

  setUpAll(() {
    registerFallbackValue(UserRole.student);
  });

  setUp(() {
    db = LocalDatabase.forTesting(NativeDatabase.memory());
    storage = InMemorySecureStorageService();
    local = AuthLocalDataSource(secureStorage: storage, db: db);
    remote = _MockRemote();
    repo = AuthRepositoryImpl(remote: remote, local: local);
  });

  tearDown(() async {
    await db.close();
  });

  AuthResponseModel _dto({String email = 'a@b.com'}) => AuthResponseModel(
        accessToken: 'acc',
        refreshToken: 'ref',
        user: UserModel(
          id: 'u1',
          email: email,
          fullName: 'Alice',
          role: 'STUDENT',
        ),
      );

  test('loginWithEmail persists tokens + cached user on success', () async {
    when(() => remote.login(
          email: any(named: 'email'),
          password: any(named: 'password'),
        )).thenAnswer((_) async => _dto());

    final session = await repo.loginWithEmail(
      email: 'a@b.com',
      password: 'Password1',
    );

    expect(session.user.email, 'a@b.com');
    expect(await storage.readAccessToken(), 'acc');
    expect(await storage.readRefreshToken(), 'ref');
    expect((await local.readCachedUser())!.email, 'a@b.com');
  });

  test('loginWithEmail maps DioException → ApiFailure', () async {
    when(() => remote.login(
          email: any(named: 'email'),
          password: any(named: 'password'),
        )).thenThrow(
      DioException(
        requestOptions: RequestOptions(path: '/auth/login'),
        response: Response(
          requestOptions: RequestOptions(path: '/auth/login'),
          statusCode: 400,
          data: {'message': 'Bad creds', 'code': 'INVALID_CREDENTIALS'},
        ),
        type: DioExceptionType.badResponse,
      ),
    );

    expect(
      () => repo.loginWithEmail(email: 'a@b.com', password: 'x'),
      throwsA(isA<Failure>()),
    );
  });

  test('getCachedUser returns null when no tokens stored', () async {
    expect(await repo.getCachedUser(), isNull);
  });

  test('logout wipes tokens + cached user even if remote fails', () async {
    when(() => remote.login(
          email: any(named: 'email'),
          password: any(named: 'password'),
        )).thenAnswer((_) async => _dto());
    await repo.loginWithEmail(email: 'a@b.com', password: 'Password1');

    when(() => remote.logout(any())).thenThrow(Exception('boom'));

    await repo.logout();
    expect(await storage.readAccessToken(), isNull);
    expect(await storage.readRefreshToken(), isNull);
    expect(await local.readCachedUser(), isNull);
  });

  test('signup forwards role and persists session', () async {
    when(() => remote.signup(
          email: any(named: 'email'),
          password: any(named: 'password'),
          fullName: any(named: 'fullName'),
          role: any(named: 'role'),
          phoneNumber: any(named: 'phoneNumber'),
        )).thenAnswer((_) async => _dto(email: 'new@example.com'));

    final session = await repo.signup(
      email: 'new@example.com',
      password: 'Password1',
      fullName: 'New',
      role: UserRole.owner,
    );

    expect(session.user.email, 'new@example.com');
    expect((await local.readCachedUser())!.email, 'new@example.com');
  });

  test('refresh requires a stored refresh token', () async {
    expect(
      () => repo.refresh(),
      throwsA(isA<Exception>()),
    );
  });
}

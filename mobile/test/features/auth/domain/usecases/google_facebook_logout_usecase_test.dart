import 'package:flutter_test/flutter_test.dart';
import 'package:sroadtutor/features/auth/domain/entities/user_role.dart';
import 'package:sroadtutor/features/auth/domain/usecases/facebook_login_usecase.dart';
import 'package:sroadtutor/features/auth/domain/usecases/google_login_usecase.dart';
import 'package:sroadtutor/features/auth/domain/usecases/logout_usecase.dart';

import '../../../../_support/fake_auth_repository.dart';

void main() {
  group('GoogleLoginUseCase', () {
    test('passes idToken + role through', () async {
      final repo = FakeAuthRepository();
      await GoogleLoginUseCase(repo)
          .call(idToken: 'tok123', role: UserRole.instructor);
      expect(repo.calls, contains('google:tok123'));
      expect(repo.cached?.role, UserRole.instructor);
    });

    test('rejects empty idToken', () {
      expect(
        () => GoogleLoginUseCase(FakeAuthRepository()).call(idToken: ''),
        throwsA(isA<ArgumentError>()),
      );
    });
  });

  group('FacebookLoginUseCase', () {
    test('passes accessToken + role through', () async {
      final repo = FakeAuthRepository();
      await FacebookLoginUseCase(repo)
          .call(accessToken: 'fbtok', role: UserRole.parent);
      expect(repo.calls, contains('facebook:fbtok'));
      expect(repo.cached?.role, UserRole.parent);
    });

    test('rejects empty accessToken', () {
      expect(
        () =>
            FacebookLoginUseCase(FakeAuthRepository()).call(accessToken: ''),
        throwsA(isA<ArgumentError>()),
      );
    });
  });

  group('LogoutUseCase', () {
    test('clears cached user via repo', () async {
      final repo = FakeAuthRepository();
      await repo.loginWithEmail(email: 'x@y.com', password: 'Password1');
      expect(repo.cached, isNotNull);

      await LogoutUseCase(repo).call();
      expect(repo.cached, isNull);
      expect(repo.calls, contains('logout'));
    });
  });
}

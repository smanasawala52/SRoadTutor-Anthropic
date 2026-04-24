import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sroadtutor/features/auth/domain/repositories/auth_repository.dart';
import 'package:sroadtutor/features/auth/presentation/providers/auth_providers.dart';
import 'package:sroadtutor/features/auth/presentation/screens/login_screen.dart';

import '../../../../_support/fake_auth_repository.dart';

void main() {
  Widget _harness({required AuthRepository repo}) {
    return ProviderScope(
      overrides: [
        authRepositoryProvider.overrideWithValue(repo),
      ],
      child: const MaterialApp(home: LoginScreen()),
    );
  }

  testWidgets('renders email + password fields + sign-in button',
      (tester) async {
    final repo = FakeAuthRepository();
    await tester.pumpWidget(_harness(repo: repo));

    expect(find.byKey(const Key('login.email')), findsOneWidget);
    expect(find.byKey(const Key('login.password')), findsOneWidget);
    expect(find.byKey(const Key('login.submit')), findsOneWidget);
  });

  testWidgets('shows validation errors when email + password are empty',
      (tester) async {
    final repo = FakeAuthRepository();
    await tester.pumpWidget(_harness(repo: repo));

    await tester.tap(find.byKey(const Key('login.submit')));
    await tester.pumpAndSettle();

    expect(find.text('Enter your email'), findsOneWidget);
    expect(find.text('Enter your password'), findsOneWidget);
    expect(repo.calls, isEmpty); // never hit the repo
  });

  testWidgets('rejects badly-formatted email', (tester) async {
    final repo = FakeAuthRepository();
    await tester.pumpWidget(_harness(repo: repo));

    await tester.enterText(find.byKey(const Key('login.email')), 'no-at');
    await tester.enterText(
      find.byKey(const Key('login.password')),
      'Password1',
    );
    await tester.tap(find.byKey(const Key('login.submit')));
    await tester.pumpAndSettle();

    expect(find.text('Not a valid email'), findsOneWidget);
    expect(repo.calls, isEmpty);
  });

  testWidgets('calls repo.loginWithEmail with normalized creds',
      (tester) async {
    final repo = FakeAuthRepository();
    await tester.pumpWidget(_harness(repo: repo));

    await tester.enterText(
      find.byKey(const Key('login.email')),
      ' Foo@Example.com ',
    );
    await tester.enterText(
      find.byKey(const Key('login.password')),
      'Password1',
    );
    await tester.tap(find.byKey(const Key('login.submit')));
    await tester.pumpAndSettle();

    expect(repo.calls, contains('loginWithEmail:foo@example.com'));
  });

  testWidgets('surfaces repo error in a snackbar', (tester) async {
    final repo = FakeAuthRepository(throwOnLogin: true);
    await tester.pumpWidget(_harness(repo: repo));

    await tester.enterText(
      find.byKey(const Key('login.email')),
      'a@b.com',
    );
    await tester.enterText(
      find.byKey(const Key('login.password')),
      'Password1',
    );
    await tester.tap(find.byKey(const Key('login.submit')));
    await tester.pump();           // kick the async
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.byType(SnackBar), findsOneWidget);
  });
}

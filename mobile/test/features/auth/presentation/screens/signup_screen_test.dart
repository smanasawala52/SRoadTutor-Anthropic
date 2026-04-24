import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sroadtutor/features/auth/domain/repositories/auth_repository.dart';
import 'package:sroadtutor/features/auth/presentation/providers/auth_providers.dart';
import 'package:sroadtutor/features/auth/presentation/screens/signup_screen.dart';

import '../../../../_support/fake_auth_repository.dart';

void main() {
  Widget _harness(AuthRepository repo) => ProviderScope(
        overrides: [authRepositoryProvider.overrideWithValue(repo)],
        child: const MaterialApp(home: SignupScreen()),
      );

  testWidgets('rejects weak password with the exact helper text',
      (tester) async {
    final repo = FakeAuthRepository();
    await tester.pumpWidget(_harness(repo));

    await tester.enterText(find.byKey(const Key('signup.name')), 'Alice');
    await tester.enterText(find.byKey(const Key('signup.email')), 'a@b.com');
    await tester.enterText(find.byKey(const Key('signup.password')), 'short');
    await tester.tap(find.byKey(const Key('signup.submit')));
    await tester.pumpAndSettle();

    expect(
      find.text('Min 8 chars, with a letter and a digit'),
      findsOneWidget,
    );
    expect(repo.calls, isEmpty);
  });

  testWidgets('submits a valid signup', (tester) async {
    final repo = FakeAuthRepository();
    await tester.pumpWidget(_harness(repo));

    await tester.enterText(find.byKey(const Key('signup.name')), 'Alice');
    await tester.enterText(
      find.byKey(const Key('signup.email')),
      'alice@example.com',
    );
    await tester.enterText(
      find.byKey(const Key('signup.password')),
      'Password1',
    );
    await tester.tap(find.byKey(const Key('signup.submit')));
    await tester.pumpAndSettle();

    expect(repo.calls, contains('signup:alice@example.com:STUDENT'));
  });
}

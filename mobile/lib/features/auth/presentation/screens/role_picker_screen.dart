import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/entities/user_role.dart';
import '../widgets/oauth_buttons.dart';

/// Shown when an OAuth provider didn't know which role the user should have
/// (first-time Google/Facebook login). The user picks a role, then taps
/// Continue → we re-trigger the OAuth flow with the role attached.
class RolePickerScreen extends ConsumerStatefulWidget {
  const RolePickerScreen({super.key});

  @override
  ConsumerState<RolePickerScreen> createState() => _RolePickerScreenState();
}

class _RolePickerScreenState extends ConsumerState<RolePickerScreen> {
  UserRole _selected = UserRole.student;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('One more step')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Tell us who you are',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 8),
              Text(
                'We\'ll tailor the home screen to your role.',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 24),
              ...UserRole.values.map(
                (r) => Card(
                  child: RadioListTile<UserRole>(
                    key: Key('rolePicker.${r.wireName}'),
                    title: Text(r.displayName),
                    value: r,
                    groupValue: _selected,
                    onChanged: (v) {
                      if (v != null) setState(() => _selected = v);
                    },
                  ),
                ),
              ),
              const Spacer(),
              GoogleSignInButton(role: _selected),
              const SizedBox(height: 12),
              FacebookSignInButton(role: _selected),
            ],
          ),
        ),
      ),
    );
  }
}

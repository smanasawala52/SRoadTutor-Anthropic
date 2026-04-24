import 'package:flutter/material.dart';
import 'package:flutter_facebook_auth/flutter_facebook_auth.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_sign_in/google_sign_in.dart';

import '../../../../main_common.dart';
import '../../domain/entities/user_role.dart';
import '../providers/auth_providers.dart';

/// "Continue with Google" button.
///
/// On first tap we pop the native Google chooser; the returned
/// [GoogleSignInAuthentication] gives us an `idToken` that the backend
/// verifies. We never handle the user's password.
class GoogleSignInButton extends ConsumerStatefulWidget {
  const GoogleSignInButton({super.key, this.role});

  /// Required when this is the user's first login for this email.
  final UserRole? role;

  @override
  ConsumerState<GoogleSignInButton> createState() => _GoogleSignInButtonState();
}

class _GoogleSignInButtonState extends ConsumerState<GoogleSignInButton> {
  bool _busy = false;

  Future<void> _handleTap() async {
    final env = ref.read(envConfigProvider);
    setState(() => _busy = true);
    try {
      final gsi = GoogleSignIn(
        serverClientId: env.googleWebClientId, // required for idToken on Android
        clientId: env.googleIosClientId,
        scopes: const ['email', 'profile', 'openid'],
      );
      await gsi.signOut(); // force chooser every time
      final account = await gsi.signIn();
      if (account == null) {
        if (mounted) setState(() => _busy = false);
        return;
      }
      final auth = await account.authentication;
      final idToken = auth.idToken;
      if (idToken == null) {
        throw StateError('Google sign-in returned no idToken');
      }
      await ref
          .read(authControllerProvider.notifier)
          .loginWithGoogle(idToken: idToken, role: widget.role);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Google sign-in failed: $e')),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: _busy ? null : _handleTap,
      icon: _busy
          ? const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Icon(Icons.g_mobiledata_rounded, size: 28),
      label: const Text('Continue with Google'),
    );
  }
}

/// "Continue with Facebook" button.
class FacebookSignInButton extends ConsumerStatefulWidget {
  const FacebookSignInButton({super.key, this.role});
  final UserRole? role;

  @override
  ConsumerState<FacebookSignInButton> createState() =>
      _FacebookSignInButtonState();
}

class _FacebookSignInButtonState extends ConsumerState<FacebookSignInButton> {
  bool _busy = false;

  Future<void> _handleTap() async {
    setState(() => _busy = true);
    try {
      final result = await FacebookAuth.instance.login(
        permissions: const ['email', 'public_profile'],
      );
      if (result.status != LoginStatus.success ||
          result.accessToken == null) {
        if (mounted) setState(() => _busy = false);
        return;
      }
      await ref.read(authControllerProvider.notifier).loginWithFacebook(
            accessToken: result.accessToken!.tokenString,
            role: widget.role,
          );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Facebook sign-in failed: $e')),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: _busy ? null : _handleTap,
      icon: _busy
          ? const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Icon(Icons.facebook, size: 22),
      label: const Text('Continue with Facebook'),
    );
  }
}

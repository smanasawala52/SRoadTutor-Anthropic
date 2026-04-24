/// Single source of truth for backend URL paths.
///
/// Keep them here so refactors touch one file, and so tests can verify path
/// strings against this class rather than duplicating the literal.
class ApiEndpoints {
  ApiEndpoints._();

  // Auth -----------------------------------------------------------------
  static const String signup = '/auth/signup';
  static const String login = '/auth/login';
  static const String google = '/auth/google';
  static const String facebook = '/auth/facebook';
  static const String refresh = '/auth/refresh';
  static const String logout = '/auth/logout';

  // Health ---------------------------------------------------------------
  static const String health = '/actuator/health';

  // Placeholders — wired up in upcoming feature tasks.
  static const String schools = '/schools';
  static const String sessions = '/sessions';
  static const String payments = '/payments';
}

// Ready-Release-Go Configuration
// Defines how PR labels map to changelog categories and version bumps

export default {
  // Version prefix for tags
  tagPrefix: 'v',

  // Categories for changelog generation
  // PRs are categorized based on labels, displayed in this order
  changeTypes: [
    {
      title: '💥 Breaking Changes',
      labels: ['breaking', 'breaking-change'],
      bump: 'major',
      weight: 100,
    },
    {
      title: '🔒 Security',
      labels: ['security'],
      bump: 'patch',
      weight: 90,
    },
    {
      title: '✨ Features',
      labels: ['feature', 'enhancement'],
      bump: 'minor',
      weight: 80,
    },
    {
      title: '🐛 Bug Fixes',
      labels: ['bug', 'bugfix', 'fix'],
      bump: 'patch',
      weight: 70,
    },
    {
      title: '🔧 Keycloak SPI',
      labels: ['keycloak-spi', 'spi'],
      bump: 'patch',
      weight: 60,
    },
    {
      title: '📚 Documentation',
      labels: ['documentation', 'docs'],
      bump: 'patch',
      weight: 50,
    },
    {
      title: '🏗️ Refactoring',
      labels: ['refactor', 'refactoring'],
      bump: 'patch',
      weight: 40,
    },
    {
      title: '📦 Dependencies',
      labels: ['dependencies', 'deps'],
      bump: 'patch',
      weight: 30,
    },
    {
      title: '🔨 Maintenance',
      labels: ['chore', 'maintenance', 'ci'],
      bump: 'patch',
      weight: 20,
    },
  ],

  // PRs with these labels are excluded from release notes
  skipLabels: ['skip-changelog', 'no-release'],

  // Only include PRs (not direct commits)
  skipCommitsWithoutPullRequest: true,

  // Comment on PRs when they're included in a release
  commentOnReleasedPullRequests: true,
};

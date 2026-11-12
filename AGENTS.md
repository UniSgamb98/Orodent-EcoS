# Repository instructions

## GitHub Actions budget

The user reserves GitHub Actions credits for releases explicitly requested by them.
- Keep workflows manual-only (`workflow_dispatch`); do not add automatic push, PR,
  tag, scheduled, or workflow-to-workflow triggers.
- Never dispatch or rerun Actions for routine development, builds, or checks.
- Run builds/tests in the working environment. Start a release workflow only after
  an explicit user request to use Actions for that release.
- Pushing changes or creating a tag is not authorization to run Actions.

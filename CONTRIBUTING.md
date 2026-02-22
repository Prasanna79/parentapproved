# Contributing to ParentApproved.tv

We're thrilled that you're interested in contributing to ParentApproved.tv! This project is built by a parent for parents, and we welcome contributions that help make the app safer, more reliable, and more useful for families.

## How Can I Help?

- **Report Bugs**: If you find something that isn't working right, please open an [issue on GitHub](https://github.com/Prasanna79/parentapproved/issues).
- **Suggest Features**: Have an idea for a new feature? We'd love to hear it! Open an issue to discuss your proposal.
- **Improve Documentation**: Found a typo or something that could be clearer? Pull requests are welcome for any documentation improvements.
- **Submit Code**: If you're a developer and want to dive into the code, check out the [Development](#development) section below.

## Development

The project is split into several components:

- **tv-app/**: The Android TV app (Kotlin, Jetpack Compose, Ktor).
- **relay/**: The Cloudflare Workers relay (TypeScript).
- **marketing/landing-page/**: The official website (HTML/CSS/JS).

### Getting Started

1. **Fork the Repository**: Create your own fork and clone it to your local machine.
2. **Setup Your Environment**: Check `CLAUDE.md` in the root directory for specific setup instructions for each component.
3. **Run Tests**: Ensure that all tests pass before making any changes.
   - **TV app**: `./gradlew testDebugUnitTest`
   - **Relay**: `cd relay && npx vitest run`
4. **Make Your Changes**: Create a new branch for your feature or bug fix.
5. **Add Tests**: Every new feature or bug fix should be accompanied by tests.
6. **Submit a Pull Request**: When you're ready, submit a PR and we'll review it together.

## Principles

- **Privacy First**: We do not collect personal data. Any contribution that introduces tracking or cloud-dependency for kids' data will be rejected.
- **Keep it Simple**: The app should be easy for parents to use and kids to navigate.
- **Safety First**: Every change must maintain the high bar we've set for children's safety.

Thank you for helping make ParentApproved.tv better for everyone!

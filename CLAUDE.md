# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Building the Plugin
```bash
gradle build --offline
```
This command must be run after any code changes to test the plugin. The `--offline` flag is required as specified in AGENTS.md.

### Running Tests
The project uses Gradle's built-in test framework. Tests are run automatically during the build process.

### Key Gradle Tasks
- `gradle clean` - Clean build artifacts
- `gradle shadowJar` - Create a fat JAR with all dependencies (automatically run during build)
- `gradle jar` - Create standard JAR (without dependencies)

## Project Architecture

### Technology Stack
- **Language**: Kotlin 1.7.10
- **Framework**: Paper API 1.20.4 (Minecraft plugin development)
- **Build System**: Gradle with Shadow plugin for fat JAR creation
- **Database**: MySQL (required)
- **Java Version**: Target JDK 17

### Core Systems Architecture

The plugin implements a comprehensive banking system for Minecraft servers with four main subsystems:

1. **Bank System** (`/src/main/java/red/man10/man10bank/`)
   - Core banking operations (deposits, withdrawals, transfers)
   - Balance management with offline player support
   - Transaction history tracking via `BankHistory` class

2. **ATM System** (`/src/main/java/red/man10/man10bank/atm/`)
   - GUI-based interface using Bukkit inventory system
   - Handles cash deposits/withdrawals through inventory interactions

3. **Cheque System** (`/src/main/java/red/man10/man10bank/cheque/`)
   - Item-based value transfer using NBT tags
   - Supports custom notes and admin-issued cheques

4. **Loan System** (`/src/main/java/red/man10/man10bank/loan/`)
   - Two types: Player-to-player loans and server revolving loans
   - Repository pattern implementation for data persistence
   - Interest calculation and payment tracking

### Command Structure
Commands are organized in `/src/main/java/red/man10/man10bank/command/`:
- `BankCommand.kt` - Core banking operations
- `ChequeCommand.kt` - Cheque issuance and management
- `LocalLoanCommand.kt` - Player-to-player loan operations
- `ServerLoanCommand.kt` - Server revolving loan system

### Data Persistence
- All data is stored in MySQL database
- Database schema files located in `/src/main/sql/`
- Connection managed through `MySQLManager` class
- Supports concurrent operations with proper threading

### Dependencies
Required:
- Vault API (economy integration)
- Paper API

Optional integrations:
- Man10CommonLibs, Man10Mail, Essentials, Man10Score
- Local JARs in `/libs/` directory

## Important Development Notes

1. **Language Requirement**: All responses, commits, pull requests, and code descriptions must be in Japanese (as specified in AGENTS.md)
   - コード内のコメントは日本語で記述すること
   - 変数名や関数名は英語でも構わないが、説明は日本語で行うこと

2. **Testing Requirement**: Always run `gradle build --offline` after code changes and report any test failures

3. **Offline Player Support**: The system is designed to work with offline players - use UUID-based operations rather than relying on online player objects

4. **Thread Safety**: Payment operations use separate threads - ensure proper synchronization when modifying balance-related code

5. **Configuration**: Runtime settings are in `config.yml` - includes MySQL settings, loan parameters, and feature toggles

6. **API Integration**: The plugin integrates with Vault for economy operations - always check Vault availability before economy operations

7. **Git Commit Policy**: コード修正後は必ず`gradle build --offline`でビルドを実行し、ビルドが成功した場合のみGitにコミットすること
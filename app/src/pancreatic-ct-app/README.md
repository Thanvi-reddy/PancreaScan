# Pancreatic CT Analysis Mobile Application

This project is a mobile application designed for the analysis of pancreatic CT scans. It provides users with the ability to create an account, log in, and access various features related to CT scan analysis.

## Features

- User authentication with account creation and login functionality.
- A dedicated screen for analyzing pancreatic CT scans.
- User-friendly interface with reusable components for input fields and buttons.

## Project Structure

```
pancreatic-ct-app
├── src
│   ├── App.tsx                  # Main entry point of the application
│   ├── assets                   # Static assets (images, fonts)
│   ├── components               # Reusable components
│   │   ├── AuthForm.tsx        # Authentication form component
│   │   ├── InputField.tsx      # Reusable input field component
│   │   └── PrimaryButton.tsx    # Primary action button component
│   ├── navigation               # Navigation configuration
│   │   └── AppNavigator.tsx     # Navigation setup
│   ├── screens                  # Application screens
│   │   ├── LoginScreen.tsx      # Login page
│   │   ├── CreateAccountScreen.tsx # Account creation page
│   │   ├── HomeScreen.tsx       # Main screen after login
│   │   └── CTAnalysisScreen.tsx  # CT scan analysis screen
│   ├── services                 # Service functions
│   │   ├── auth.ts              # Authentication functions
│   │   ├── api.ts               # API call functions
│   │   └── ctProcessor.ts       # CT scan processing functions
│   ├── hooks                    # Custom hooks
│   │   └── useAuth.ts          # Authentication state management
│   ├── styles                   # Global styles
│   │   └── globals.ts           # Global style configurations
│   └── types                    # TypeScript types
│       └── index.ts             # Type definitions
├── package.json                 # NPM configuration
├── tsconfig.json                # TypeScript configuration
├── babel.config.js              # Babel configuration
└── README.md                    # Project documentation
```

## Installation

1. Clone the repository:
   ```
   git clone <repository-url>
   ```

2. Navigate to the project directory:
   ```
   cd pancreatic-ct-app
   ```

3. Install the dependencies:
   ```
   npm install
   ```

## Usage

To start the application, run:
```
npm start
```

This will launch the application in your default web browser or mobile simulator.

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue for any suggestions or improvements.

## License

This project is licensed under the MIT License.
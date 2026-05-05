export interface User {
    name: string;
    mobileNumber: string;
    email: string;
    password: string;
}

export interface AuthResponse {
    token: string;
    user: User;
}

export interface CTScan {
    id: string;
    patientId: string;
    scanDate: string;
    imageUrl: string;
    analysisResults: string;
}
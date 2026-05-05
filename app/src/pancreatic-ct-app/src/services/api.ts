import axios from 'axios';

const API_BASE_URL = 'https://api.example.com'; // Replace with your actual API base URL

export const createAccount = async (name, mobile, email, password) => {
    try {
        const response = await axios.post(`${API_BASE_URL}/create-account`, {
            name,
            mobile,
            email,
            password,
        });
        return response.data;
    } catch (error) {
        throw new Error(error.response.data.message || 'Error creating account');
    }
};

export const login = async (email, password) => {
    try {
        const response = await axios.post(`${API_BASE_URL}/login`, {
            email,
            password,
        });
        return response.data;
    } catch (error) {
        throw new Error(error.response.data.message || 'Error logging in');
    }
};

export const fetchCTScanData = async (scanId) => {
    try {
        const response = await axios.get(`${API_BASE_URL}/ct-scans/${scanId}`);
        return response.data;
    } catch (error) {
        throw new Error(error.response.data.message || 'Error fetching CT scan data');
    }
};
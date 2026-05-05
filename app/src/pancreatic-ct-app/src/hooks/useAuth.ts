import { useState, useEffect } from 'react';
import { createAccount, login } from '../services/auth';

const useAuth = () => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        // Check for existing user session or token
        const checkUserSession = async () => {
            // Logic to check user session
            setLoading(false);
        };

        checkUserSession();
    }, []);

    const handleLogin = async (email, password) => {
        setLoading(true);
        try {
            const loggedInUser = await login(email, password);
            setUser(loggedInUser);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const handleCreateAccount = async (name, mobile, email, password) => {
        setLoading(true);
        try {
            const newUser = await createAccount(name, mobile, email, password);
            setUser(newUser);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return {
        user,
        loading,
        error,
        handleLogin,
        handleCreateAccount,
    };
};

export default useAuth;
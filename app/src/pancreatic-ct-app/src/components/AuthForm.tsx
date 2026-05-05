import React, { useState } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import InputField from './InputField';
import PrimaryButton from './PrimaryButton';
import { createAccount } from '../services/auth';

const AuthForm = ({ isLogin, onToggle }) => {
    const [name, setName] = useState('');
    const [mobile, setMobile] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');

    const handleSubmit = async () => {
        if (isLogin) {
            // Handle login logic
        } else {
            try {
                await createAccount({ name, mobile, email, password });
                // Redirect to login page after account creation
            } catch (err) {
                setError(err.message);
            }
        }
    };

    return (
        <View style={styles.container}>
            {error ? <Text style={styles.error}>{error}</Text> : null}
            {!isLogin && (
                <>
                    <InputField
                        label="Name"
                        value={name}
                        onChangeText={setName}
                    />
                    <InputField
                        label="Mobile Number"
                        value={mobile}
                        onChangeText={setMobile}
                        keyboardType="phone-pad"
                    />
                </>
            )}
            <InputField
                label="Email"
                value={email}
                onChangeText={setEmail}
                keyboardType="email-address"
            />
            <InputField
                label="Password"
                value={password}
                onChangeText={setPassword}
                secureTextEntry
            />
            <PrimaryButton title={isLogin ? "Login" : "Create Account"} onPress={handleSubmit} />
            <Text style={styles.toggleText} onPress={onToggle}>
                {isLogin ? "Don't have an account? Create one" : "Already have an account? Login"}
            </Text>
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        padding: 20,
    },
    error: {
        color: 'red',
        marginBottom: 10,
    },
    toggleText: {
        marginTop: 10,
        color: 'blue',
        textAlign: 'center',
    },
});

export default AuthForm;
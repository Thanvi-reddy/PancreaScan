import React from 'react';
import { View, Text, Button } from 'react-native';

const HomeScreen = () => {
    return (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
            <Text>Welcome to the Pancreatic CT Analysis App!</Text>
            <Button title="Analyze CT Scan" onPress={() => { /* Navigate to CTAnalysisScreen */ }} />
            <Button title="Logout" onPress={() => { /* Handle logout functionality */ }} />
        </View>
    );
};

export default HomeScreen;
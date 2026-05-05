import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

const CTAnalysisScreen = () => {
    return (
        <View style={styles.container}>
            <Text style={styles.title}>Pancreatic CT Analysis</Text>
            <Text style={styles.description}>
                This screen is dedicated to displaying and analyzing pancreatic CT scans.
            </Text>
            {/* Additional components and functionality for CT analysis will be implemented here */}
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        padding: 16,
    },
    title: {
        fontSize: 24,
        fontWeight: 'bold',
    },
    description: {
        fontSize: 16,
        textAlign: 'center',
        marginTop: 8,
    },
});

export default CTAnalysisScreen;
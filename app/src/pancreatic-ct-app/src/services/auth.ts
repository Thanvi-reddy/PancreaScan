import { useState } from 'react';

interface User {
  name: string;
  mobile: string;
  email: string;
  password: string;
}

const users: User[] = [];

export const createAccount = (name: string, mobile: string, email: string, password: string): boolean => {
  const newUser: User = { name, mobile, email, password };
  users.push(newUser);
  return true; // Account creation successful
};

export const login = (email: string, password: string): boolean => {
  const user = users.find(user => user.email === email && user.password === password);
  return user !== undefined; // Return true if login is successful
};
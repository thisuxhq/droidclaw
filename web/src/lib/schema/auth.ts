import { object, string, pipe, minLength, email } from 'valibot';

export const signupSchema = object({
	name: pipe(string(), minLength(4)),
	email: pipe(string(), minLength(1), email()),
	password: pipe(string(), minLength(8))
});

export const loginSchema = object({
	email: pipe(string(), minLength(1), email()),
	password: pipe(string(), minLength(8))
});

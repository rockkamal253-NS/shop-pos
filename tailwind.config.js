/** @type {import('tailwindcss').Config} */
module.exports = {
    content: [
        './src/main/resources/templates/**/*.html',
        './src/main/resources/static/js/**/*.js',
    ],
    theme: {
        extend: {
            colors: {
                // Single primary accent
                primary: {
                    50: '#eef2ff', 100: '#e0e7ff', 200: '#c7d2fe', 300: '#a5b4fc',
                    400: '#818cf8', 500: '#6366f1', 600: '#4f46e5', 700: '#4338ca',
                    800: '#3730a3', 900: '#312e81',
                },
                // Semantic colors
                success: {50: '#ecfdf5', 100: '#d1fae5', 500: '#10b981', 600: '#059669', 700: '#047857', 800: '#065f46'},
                warning: {50: '#fffbeb', 100: '#fef3c7', 500: '#f59e0b', 600: '#d97706', 700: '#b45309'},
                danger: {50: '#fef2f2', 100: '#fee2e2', 500: '#ef4444', 600: '#dc2626', 700: '#b91c1c', 800: '#991b1b'},
            },
            fontFamily: {
                sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
            },
            // Defined type scale: 12 / 14 / 16 / 20 / 28
            fontSize: {
                xs: ['12px', {lineHeight: '16px'}],
                sm: ['14px', {lineHeight: '20px'}],
                base: ['16px', {lineHeight: '24px'}],
                lg: ['20px', {lineHeight: '28px'}],
                xl: ['28px', {lineHeight: '36px'}],
            },
        },
    },
};

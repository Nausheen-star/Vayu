import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 2000,
    duration: '1m',
    // thresholds: {
    //     http_req_failed: ['rate<0.01'], // Allow less than 1% requests to fail
    //     http_req_duration: ['p(95)<2000'], // 95% of requests should complete within 2s
    // },
};

export default function () {
    const url = 'http://localhost:8080';

    const body =
        `
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta http-equiv="X-UA-Compatible" content="ie=edge">
                <title>Vayu Web Server Portfolio</title>
                <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap" rel="stylesheet">
                <style>
                    body {
                        font-family: 'Roboto', sans-serif;
                        background: url('background-image.jpg') no-repeat center center fixed;
                        background-size: cover;
                        margin: 0;
                        padding: 0;
                        color: #333;
                    }
                </style>
            </head>
            <body>
                <header>
                    <h1>Welcome to My Portfolio</h1>
                    <p>Built with Vayu - High-Performance Web Server</p>
                </header>
            </body>
            </html>
        `;

    const headers = {
        'Content-Type': 'text/html',
    };

    const response = http.get(url, body, { headers });

    // Basic checks to ensure the server responds correctly
    check(response, {
        'status is 200': (response) => response.status === 200,
        'response time < 2000ms': (r) => response.timings.duration < 2000,
    });

    sleep(1); // Wait for 1 second between iterations
}


// k6 run --out cloud test.js
// k6 run test.js
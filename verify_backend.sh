#!/bin/bash
echo "Testing /connection_token..."
curl -s -X POST http://localhost:3000/connection_token
echo ""

echo "Testing /store/auth/prepare-setup..."
curl -s -X POST http://localhost:3000/store/auth/prepare-setup
echo ""

echo "Testing /store/auth/login-by-card..."
curl -s -X POST -H "Content-Type: application/json" -d '{"payment_method_id":"pm_test_123"}' http://localhost:3000/store/auth/login-by-card
echo ""

## Summary
<!-- What does this PR do? 1-2 sentences. -->

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Refactor (no functional change)
- [ ] Documentation
- [ ] Build / CI / DevOps

## Changes Made
<!-- List the key changes. Be specific. -->
- 
- 

## Testing
- [ ] Unit tests added / updated
- [ ] Integration tests added / updated
- [ ] Tested locally with `docker compose up`

## Checklist
- [ ] Code follows the domain-driven package structure
- [ ] No entity leakage to controllers (DTOs used throughout)
- [ ] `@Transactional` only on service layer methods
- [ ] New financial operations include idempotency key support
- [ ] All new endpoints documented with `@Operation` (Swagger)
- [ ] No hardcoded secrets or credentials

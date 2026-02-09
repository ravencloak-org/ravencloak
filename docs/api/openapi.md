# OpenAPI Specification

The auth backend exposes an OpenAPI 3.0 spec at runtime. This page renders the spec inline using [neoteroi-mkdocs](https://github.com/Neoteroi/mkdocs-plugins).

## Generating the Spec

With the application running locally:

```bash
curl -s http://localhost:8080/v3/api-docs -o docs/openapi.json
```

Then rebuild the docs:

```bash
mkdocs build
```

## API Reference

!!! info
    Once the spec file is committed at `docs/api/openapi.json`, add the OAD directive here to render it inline. See the [neoteroi-mkdocs docs](https://www.neoteroi.dev/mkdocs-plugins/oad/) for syntax.

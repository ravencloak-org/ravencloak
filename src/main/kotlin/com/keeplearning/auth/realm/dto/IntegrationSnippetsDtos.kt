package com.keeplearning.auth.realm.dto

data class IntegrationSnippetsResponse(
    val keycloakUrl: String,
    val realmName: String,
    val clientId: String,
    val snippets: IntegrationSnippets
)

data class IntegrationSnippets(
    val vanillaJs: String,
    val react: String,
    val vue: String
)

object IntegrationSnippetGenerator {

    fun generate(keycloakUrl: String, realmName: String, clientId: String): IntegrationSnippets {
        return IntegrationSnippets(
            vanillaJs = generateVanillaJs(keycloakUrl, realmName, clientId),
            react = generateReact(keycloakUrl, realmName, clientId),
            vue = generateVue(keycloakUrl, realmName, clientId)
        )
    }

    private fun generateVanillaJs(keycloakUrl: String, realmName: String, clientId: String): String {
        return """
import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
  url: "$keycloakUrl",
  realm: "$realmName",
  clientId: "$clientId"
});

// Initialize Keycloak
keycloak.init({
  onLoad: "login-required",
  checkLoginIframe: false,
  pkceMethod: "S256"
}).then(authenticated => {
  if (authenticated) {
    console.log("User authenticated");
    console.log("Token:", keycloak.token);

    // Access user info
    keycloak.loadUserProfile().then(profile => {
      console.log("User:", profile.email);
    });
  }
}).catch(error => {
  console.error("Authentication failed:", error);
});

// Token refresh
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30).catch(() => {
    console.log("Token refresh failed, re-authenticating...");
    keycloak.login();
  });
};

// Use token in API calls
async function fetchWithAuth(url, options = {}) {
  await keycloak.updateToken(30);
  return fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      Authorization: `Bearer ${'$'}{keycloak.token}`
    }
  });
}
        """.trimIndent()
    }

    private fun generateReact(keycloakUrl: String, realmName: String, clientId: String): String {
        return """
// useKeycloak.ts
import { useState, useEffect, useCallback } from "react";
import Keycloak from "keycloak-js";

const keycloakConfig = {
  url: "$keycloakUrl",
  realm: "$realmName",
  clientId: "$clientId"
};

const keycloak = new Keycloak(keycloakConfig);

export function useKeycloak() {
  const [authenticated, setAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [token, setToken] = useState<string | undefined>();
  const [user, setUser] = useState<any>(null);

  useEffect(() => {
    keycloak.init({
      onLoad: "login-required",
      checkLoginIframe: false,
      pkceMethod: "S256"
    }).then(auth => {
      setAuthenticated(auth);
      setToken(keycloak.token);
      if (auth) {
        keycloak.loadUserProfile().then(setUser);
      }
    }).finally(() => {
      setLoading(false);
    });

    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).then(refreshed => {
        if (refreshed) setToken(keycloak.token);
      });
    };
  }, []);

  const login = useCallback(() => keycloak.login(), []);
  const logout = useCallback(() => keycloak.logout(), []);

  const fetchWithAuth = useCallback(async (url: string, options: RequestInit = {}) => {
    await keycloak.updateToken(30);
    return fetch(url, {
      ...options,
      headers: {
        ...options.headers,
        Authorization: `Bearer ${'$'}{keycloak.token}`
      }
    });
  }, []);

  return { authenticated, loading, token, user, login, logout, fetchWithAuth };
}

// Usage in component:
// const { authenticated, user, fetchWithAuth } = useKeycloak();
        """.trimIndent()
    }

    private fun generateVue(keycloakUrl: String, realmName: String, clientId: String): String {
        return """
// keycloak.ts
import Keycloak from "keycloak-js";
import { ref, readonly } from "vue";
import type { App } from "vue";

const keycloakConfig = {
  url: "$keycloakUrl",
  realm: "$realmName",
  clientId: "$clientId"
};

const keycloak = new Keycloak(keycloakConfig);
const authenticated = ref(false);
const loading = ref(true);
const token = ref<string>();
const user = ref<any>(null);

export const useKeycloak = () => ({
  keycloak,
  authenticated: readonly(authenticated),
  loading: readonly(loading),
  token: readonly(token),
  user: readonly(user),
  login: () => keycloak.login(),
  logout: () => keycloak.logout(),
  fetchWithAuth: async (url: string, options: RequestInit = {}) => {
    await keycloak.updateToken(30);
    return fetch(url, {
      ...options,
      headers: {
        ...options.headers,
        Authorization: `Bearer ${'$'}{keycloak.token}`
      }
    });
  }
});

export const keycloakPlugin = {
  install(app: App) {
    keycloak.init({
      onLoad: "login-required",
      checkLoginIframe: false,
      pkceMethod: "S256"
    }).then(auth => {
      authenticated.value = auth;
      token.value = keycloak.token;
      if (auth) {
        keycloak.loadUserProfile().then(profile => {
          user.value = profile;
        });
      }
    }).finally(() => {
      loading.value = false;
    });

    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).then(refreshed => {
        if (refreshed) token.value = keycloak.token;
      });
    };

    app.provide("keycloak", useKeycloak());
  }
};

// main.ts usage:
// import { keycloakPlugin } from "./keycloak";
// app.use(keycloakPlugin);
//
// In component:
// const { authenticated, user, fetchWithAuth } = inject("keycloak");
        """.trimIndent()
    }
}

import { Injectable } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { firstValueFrom } from 'rxjs';
import { OidcDiscoveryDocument } from '../interfaces/oidc-discovery.interface';

@Injectable()
export class OidcDiscoveryService {
  private discoveryCache: OidcDiscoveryDocument | null = null;
  private cacheExpiry = 0;
  private readonly CACHE_TTL = 3600000; // 1 hour

  constructor(
    private readonly httpService: HttpService,
    private readonly configService: ConfigService,
  ) {}

  async getDiscoveryDocument(): Promise<OidcDiscoveryDocument> {
    const now = Date.now();
    if (this.discoveryCache && now < this.cacheExpiry) {
      return this.discoveryCache;
    }

    const issuerUrl = this.configService.get<string>('oidc.issuer');
    const discoveryUrl = `${issuerUrl}/.well-known/openid-configuration`;

    try {
      const { data } = await firstValueFrom(
        this.httpService.get<OidcDiscoveryDocument>(discoveryUrl),
      );
      this.discoveryCache = data;
      this.cacheExpiry = now + this.CACHE_TTL;
      return data;
    } catch (error) {
      if (this.discoveryCache) {
        return this.discoveryCache;
      }
      throw new Error(`Failed to fetch OIDC discovery document: ${error.message}`);
    }
  }

  async getJwksUri(): Promise<string> {
    const doc = await this.getDiscoveryDocument();
    return doc.jwks_uri;
  }

  async getAuthorizationEndpoint(): Promise<string> {
    const doc = await this.getDiscoveryDocument();
    return doc.authorization_endpoint;
  }

  async getTokenEndpoint(): Promise<string> {
    const doc = await this.getDiscoveryDocument();
    return doc.token_endpoint;
  }

  async getUserinfoEndpoint(): Promise<string> {
    const doc = await this.getDiscoveryDocument();
    return doc.userinfo_endpoint;
  }

  async getIntrospectionEndpoint(): Promise<string> {
    const doc = await this.getDiscoveryDocument();
    return doc.introspection_endpoint || '';
  }

  async getSupportedScopes(): Promise<string[]> {
    const doc = await this.getDiscoveryDocument();
    return doc.scopes_supported || ['openid', 'profile', 'email'];
  }

  async getSupportedResponseTypes(): Promise<string[]> {
    const doc = await this.getDiscoveryDocument();
    return doc.response_types_supported || ['code', 'id_token', 'token'];
  }

  async getSupportedGrantTypes(): Promise<string[]> {
    const doc = await this.getDiscoveryDocument();
    return doc.grant_types_supported || [
      'authorization_code',
      'client_credentials',
      'refresh_token',
    ];
  }

  clearCache(): void {
    this.discoveryCache = null;
    this.cacheExpiry = 0;
  }
}

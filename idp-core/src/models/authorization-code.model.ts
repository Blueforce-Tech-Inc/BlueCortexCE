import { Schema, Document } from 'mongoose';
import { v4 as uuidv4 } from 'uuid';
import { BaseEntity } from './base.entity';

export enum AuthorizationCodeStatus {
  ACTIVE = 'active',
  USED = 'used',
  EXPIRED = 'expired',
  REVOKED = 'revoked',
}

export interface AuthorizationCodeDocument extends Document {
  _id: string;
  code: string;
  userId: string;
  clientId: string;
  redirectUri: string;
  scope: string;
  nonce?: string;
  state?: string;
  codeChallenge?: string;
  codeChallengeMethod?: string;
  resource?: string;
  claims?: Record<string, any>;
  status: AuthorizationCodeStatus;
  usedAt?: Date;
  expiresAt: Date;
  isExpired(): boolean;
}

export const AuthorizationCodeSchema = new Schema<AuthorizationCodeDocument>(
  {
    _id: { type: String, default: () => uuidv4() },
    code: { type: String, required: true, unique: true, index: true },
    userId: { type: String, required: true, ref: 'User' },
    clientId: { type: String, required: true, ref: 'Client' },
    redirectUri: { type: String, required: true },
    scope: { type: String, required: true },
    nonce: String,
    state: String,
    codeChallenge: String,
    codeChallengeMethod: { type: String, enum: ['plain', 'S256'] },
    resource: String,
    claims: Schema.Types.Mixed,
    status: {
      type: String,
      enum: Object.values(AuthorizationCodeStatus),
      default: AuthorizationCodeStatus.ACTIVE,
    },
    usedAt: Date,
    expiresAt: { type: Date, required: true },
  },
  BaseEntity.schemaOptions,
);

AuthorizationCodeSchema.index({ code: 1 });
AuthorizationCodeSchema.index({ userId: 1 });
AuthorizationCodeSchema.index({ clientId: 1 });
AuthorizationCodeSchema.index({ expiresAt: 1 }, { expireAfterSeconds: 0 });
AuthorizationCodeSchema.index({ status: 1 });

AuthorizationCodeSchema.methods.isExpired = function () {
  return new Date() > this.expiresAt;
};

export interface AuthorizationRequest {
  clientId: string;
  redirectUri: string;
  responseType: string;
  scope: string;
  state?: string;
  nonce?: string;
  codeChallenge?: string;
  codeChallengeMethod?: string;
  resource?: string;
  claims?: Record<string, any>;
  maxAge?: number;
  prompt?: string;
  loginHint?: string;
}

export interface TokenRequest {
  grantType: string;
  code?: string;
  redirectUri?: string;
  clientId: string;
  clientSecret?: string;
  codeVerifier?: string;
  scope?: string;
  refreshToken?: string;
  username?: string;
  password?: string;
  resource?: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken?: string;
  scope: string;
  idToken?: string;
}

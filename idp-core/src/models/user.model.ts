import { Schema } from 'mongoose';
import { BaseEntity } from './base.entity';

export interface ID {
  provider: string;
  value: string;
}

export interface BaseUserDocument extends Document {
  _id: string;
  username: string;
  email: string;
  emailVerified: boolean;
  phoneNumber?: string;
  phoneVerified: boolean;
  passwordHash?: string;
  firstName?: string;
  lastName?: string;
  fullName?: string;
  nickname?: string;
  preferredUsername?: string;
  profile?: string;
  picture?: string;
  website?: string;
  gender?: string;
  birthdate?: string;
  zoneinfo?: string;
  locale?: string;
  address?: {
    formatted?: string;
    streetAddress?: string;
    locality?: string;
    region?: string;
    postalCode?: string;
    country?: string;
  };
  ids: ID[];
  groups: string[];
  active: boolean;
  maxConcurrentSessions: number;
  loginCount: number;
  lastLoginAt?: Date;
  lastLoginIp?: string;
  failedLoginAttempts: number;
  lockUntil?: Date;
  metadata: Record<string, any>;
  securityLevel: string;
  refreshTokenRotation: boolean;
  toProfile: (scope?: string) => any;
  toAdminProfile: () => any;
  incLoginCount: () => Promise<any>;
  recordFailedAttempt: () => Promise<any>;
  resetFailedAttempts: () => Promise<any>;
  isLocked: () => boolean;
}

const IdSchema = new Schema<ID>(
  {
    provider: { type: String, required: true },
    value: { type: String, required: true },
  },
  { _id: false },
);

export const BaseUserSchema = new Schema<BaseUserDocument>(
  {
    _id: { type: String },
    username: { type: String, unique: true, required: true, lowercase: true },
    email: { type: String, unique: true, sparse: true, lowercase: true },
    emailVerified: { type: Boolean, default: false },
    phoneNumber: { type: String, unique: true, sparse: true },
    phoneVerified: { type: Boolean, default: false },
    passwordHash: { type: String },
    firstName: String,
    lastName: String,
    fullName: String,
    nickname: String,
    preferredUsername: String,
    profile: String,
    picture: String,
    website: String,
    gender: { type: String, enum: ['male', 'female', 'other', ''] },
    birthdate: String,
    zoneinfo: { type: String, default: 'Asia/Shanghai' },
    locale: { type: String, default: 'zh-CN' },
    address: {
      formatted: String,
      streetAddress: String,
      locality: String,
      region: String,
      postalCode: String,
      country: String,
    },
    ids: { type: [IdSchema], default: [] },
    groups: { type: [String], default: [] },
    active: { type: Boolean, default: true },
    maxConcurrentSessions: { type: Number, default: 5 },
    loginCount: { type: Number, default: 0 },
    lastLoginAt: Date,
    lastLoginIp: String,
    failedLoginAttempts: { type: Number, default: 0 },
    lockUntil: Date,
    metadata: { type: Schema.Types.Mixed, default: {} },
    securityLevel: { type: String, default: 'normal' },
    refreshTokenRotation: { type: Boolean, default: true },
  },
  {
    ...BaseEntity.schemaOptions,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

BaseUserSchema.index({ username: 1 });
BaseUserSchema.index({ email: 1 });
BaseUserSchema.index({ 'ids.provider': 1, 'ids.value': 1 });
BaseUserSchema.index({ groups: 1 });
BaseUserSchema.index({ createdAt: -1 });

BaseUserSchema.methods.toProfile = function (scope = '') {
  const scopes = scope.split(' ').filter(Boolean);
  const profile: any = {
    sub: this._id,
  };

  if (scopes.includes('profile')) {
    Object.assign(profile, {
      name: this.fullName || `${this.firstName || ''} ${this.lastName || ''}`.trim(),
      given_name: this.firstName,
      family_name: this.lastName,
      nickname: this.nickname,
      preferred_username: this.preferredUsername || this.username,
      profile: this.profile,
      picture: this.picture,
      website: this.website,
      gender: this.gender,
      birthdate: this.birthdate,
      zoneinfo: this.zoneinfo,
      locale: this.locale,
      updated_at: Math.floor(this.updatedAt.getTime() / 1000),
    });

    if (this.address) {
      profile.address = {
        formatted: this.address.formatted,
        street_address: this.address.streetAddress,
        locality: this.address.locality,
        region: this.address.region,
        postal_code: this.address.postalCode,
        country: this.address.country,
      };
    }
  }

  if (scopes.includes('email')) {
    profile.email = this.email;
    profile.email_verified = this.emailVerified;
  }

  if (scopes.includes('phone')) {
    profile.phone_number = this.phoneNumber;
    profile.phone_number_verified = this.phoneVerified;
  }

  if (scopes.includes('roles')) {
    profile.groups = this.groups;
    profile.security_level = this.securityLevel;
  }

  return profile;
};

BaseUserSchema.methods.toAdminProfile = function () {
  return {
    id: this._id,
    username: this.username,
    email: this.email,
    emailVerified: this.emailVerified,
    phoneNumber: this.phoneNumber,
    phoneVerified: this.phoneVerified,
    firstName: this.firstName,
    lastName: this.lastName,
    fullName: this.fullName,
    nickname: this.nickname,
    groups: this.groups,
    active: this.active,
    securityLevel: this.securityLevel,
    loginCount: this.loginCount,
    lastLoginAt: this.lastLoginAt,
    lastLoginIp: this.lastLoginIp,
    failedLoginAttempts: this.failedLoginAttempts,
    locked: this.isLocked(),
    lockUntil: this.lockUntil,
    maxConcurrentSessions: this.maxConcurrentSessions,
    refreshTokenRotation: this.refreshTokenRotation,
    createdAt: this.createdAt,
    updatedAt: this.updatedAt,
  };
};

BaseUserSchema.methods.incLoginCount = function () {
  return this.model('User').findByIdAndUpdate(
    this._id,
    {
      $inc: { loginCount: 1 },
      $set: { lastLoginAt: new Date() },
    },
    { new: true },
  );
};

BaseUserSchema.methods.recordFailedAttempt = function () {
  const update: any = { $inc: { failedLoginAttempts: 1 } };
  if (this.failedLoginAttempts + 1 >= 5) {
    update.$set = {
      lockUntil: new Date(Date.now() + 15 * 60 * 1000),
    };
  }
  return this.model('User').findByIdAndUpdate(this._id, update, { new: true });
};

BaseUserSchema.methods.resetFailedAttempts = function () {
  return this.model('User').findByIdAndUpdate(
    this._id,
    { $set: { failedLoginAttempts: 0, lockUntil: undefined } },
    { new: true },
  );
};

BaseUserSchema.methods.isLocked = function () {
  return this.lockUntil && this.lockUntil > new Date();
};

BaseUserSchema.pre('save', function (next) {
  if (this.firstName || this.lastName) {
    this.fullName = `${this.firstName || ''} ${this.lastName || ''}`.trim();
  }
  if (!this.preferredUsername) {
    this.preferredUsername = this.username;
  }
  next();
});

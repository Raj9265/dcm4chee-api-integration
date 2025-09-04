/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/// <reference path="keycloak.d.ts"/>

import {Observable, from} from "rxjs";

declare var Keycloak: any;
import {EventEmitter, Injectable} from '@angular/core';
import {DcmWebApp} from "../../models/dcm-web-app";
import {AppService} from "../../app.service";
import {Globalvar} from "../../constants/globalvar";
import {of, Subject} from "../../../../node_modules/rxjs";
import {j4care} from "../j4care.service";
import {User} from "../../models/user";
import * as _ from 'lodash-es';
import {promise} from "selenium-webdriver";
import {flatMap, map, switchMap} from "rxjs/operators";
import {LocalLanguageObject} from "../../interfaces";
import {J4careHttpService} from "../j4care-http.service";

type KeycloakClient = KeycloakModule.KeycloakClient|any;

@Injectable()
export class KeycloakService {
    static languageProfilePath = 'attributes.locale[0]';
    static keycloakAuth: KeycloakClient;
    static keycloakConfig:any;
    private setTokenSource = new Subject<any>();
    private setUserSource = new Subject<any>();
    private userInfo;
    keycloakConfigName = `keycloak_config_${location.host}`;
    // static getTokenObs =
    constructor(
       private mainservice:AppService
    ){
        try{
            KeycloakService.keycloakConfig = JSON.parse(localStorage.getItem(this.keycloakConfigName));
        }catch (e) {
            j4care.log("keycloakConfig probably not set",e);
        }
    }
    init(options?: any) {
        if(KeycloakService.keycloakConfig){
            // this.mainservice.updateGlobal("notSecure",false);
            KeycloakService.keycloakAuth = new Keycloak(KeycloakService.keycloakConfig);
            return j4care.promiseToObservable(new Promise((resolve, reject) => {
                // @ts-ignore
                KeycloakService.keycloakAuth.init(Globalvar.KEYCLOAK_OPTIONS())
                    .then(authenticated => {
                        if (authenticated) {
                            this.setTokenSource.next(KeycloakService.keycloakAuth);
                            console.log('User is authenticated');
                            KeycloakService.keycloakAuth.loadUserProfile().then(user=>{
                                this.checkLanguageBasedOnKeycloak(user);
                                this.setUserInfo({
                                    userProfile:user,
                                    tokenParsed:KeycloakService.keycloakAuth.tokenParsed,
                                    authServerUrl:KeycloakService.keycloakAuth.authServerUrl,
                                    realm:KeycloakService.keycloakAuth.realm
                                });
                                this.mainservice.setSecured(true);
                                this.mainservice.updateGlobal("notSecure",false);
                                resolve(null);
                            }).catch((err: unknown)=>{
                                this.mainservice.setSecured(false);
                                this.mainservice.updateGlobal("notSecure",true);
                                console.error("err on loadingUserProfile",err);
                                this.checkLanguageBasedOnKeycloak(undefined);
                                reject(err);
                            });
                            // Your application logic here
                        } else {
                            console.warn('User is not authenticated');
                        }
                    })
                    .catch((err: unknown) => {
                        console.error('Failed to initialize Keycloak', err);
                    });
      /*          KeycloakService.keycloakAuth.init(Globalvar.KEYCLOAK_OPTIONS())
                    .then((authenticated) => {
                        this.setTokenSource.next(KeycloakService.keycloakAuth);
                        KeycloakService.keycloakAuth.loadUserProfile().success(user=>{
                            this.checkLanguageBasedOnKeycloak(user);
                            this.setUserInfo({
                                userProfile:user,
                                tokenParsed:KeycloakService.keycloakAuth.tokenParsed,
                                authServerUrl:KeycloakService.keycloakAuth.authServerUrl,
                                realm:KeycloakService.keycloakAuth.realm
                            });
                            this.mainservice.setSecured(true);
                            this.mainservice.updateGlobal("notSecure",false);
                            resolve(null);
                        }).error(err=>{
                            this.mainservice.setSecured(false);
                            this.mainservice.updateGlobal("notSecure",true);
                            console.error("err on loadingUserProfile",err);
                            this.checkLanguageBasedOnKeycloak(undefined);
                            reject(err);
                        });
                    })
                    .error((errorData: any) => {
                        this.mainservice.setSecured(false);
                        this.mainservice.updateGlobal("notSecure",true);
                        reject(errorData);
                    });*/
            }))
        }else{
            return this.mainservice.getKeycloakJson().pipe(flatMap((keycloakJson:any)=>{
                if(!_.isEmpty(keycloakJson)){
                    localStorage.setItem(this.keycloakConfigName,JSON.stringify(keycloakJson));
                    KeycloakService.keycloakAuth = new Keycloak(keycloakJson);
                    return j4care.promiseToObservable(new Promise((resolve, reject) => {
                        KeycloakService.keycloakAuth.init(Globalvar.KEYCLOAK_OPTIONS())
                            .then(() => {
                                this.setTokenSource.next(KeycloakService.keycloakAuth.token);
                                resolve(null);
                            })
                            .catch((errorData: any) => {
                                reject(errorData);
                            });
                    }))
                }else{
                    this.setUserInfo(undefined);
                    this.setTokenSource.next("");
                    this.mainservice.updateGlobal("notSecure",true);
                    return of([]);
                }
            }))
        }
    }

    setUserInfo(user){
        this.userInfo = user;
        this.setUserSource.next(user);
    }
    getUserInfo():Observable<any>{
        if(this.userInfo){
            return of(this.userInfo);
        }else{
            return this.setUserSource.asObservable();
        }
    }
    getTokenObs():Observable<any>{
        return this.setTokenSource.asObservable();
    }

    authenticated(): boolean {
        return KeycloakService.keycloakAuth.authenticated;
    }
    isTokenExpired(minValidity:number):boolean{
        return KeycloakService.keycloakAuth.isTokenExpired(minValidity);
    }

    login() {
        KeycloakService.keycloakAuth.login();
    }

    logout() {
        KeycloakService.keycloakAuth.logout();
    }

    account() {
        KeycloakService.keycloakAuth.accountManagement();
    }

    getToken():Observable<any>{
        if(_.hasIn(this.mainservice,"global.notSecure") && this.mainservice.global.notSecure){
            return of({});
        }else{
            if(KeycloakService.keycloakAuth && KeycloakService.keycloakAuth.authenticated){
                console.log("KeycloakService.keycloakAuth",KeycloakService.keycloakAuth)
                if(KeycloakService.keycloakAuth.isTokenExpired(5)){
                    return j4care.promiseToObservable(new Promise<any>((resolve, reject) => {
                        if (KeycloakService.keycloakAuth.token) {
                            KeycloakService.keycloakAuth
                                .updateToken(5)
                                .then(() => {
                                    resolve(<any>KeycloakService.keycloakAuth);
                                })
                                .catch((e:unknown) => {
                                    reject($localize `:@@keycloak.failed_to_refresh_token:Failed to refresh token`);
                                });
                        } else {
                            reject($localize `:@@keycloak.not_logged_in:Not logged in`);
                        }
                    }));
                }else{
                    return of(KeycloakService.keycloakAuth);
                }
            }else{
                return this.getTokenObs();
            }
        }
    }

    checkLanguageBasedOnKeycloak(user){
        console.log("user",user);
        if(user && _.hasIn(user,KeycloakService.languageProfilePath)){
            const keycloakLanguageCode = _.get(user,KeycloakService.languageProfilePath);
            const regex = /dcm4chee-arc\/ui2\/(\w{2})\//gm;
            let match;
            if ((match = regex.exec(location.href)) !== null) {
                if(match[1] != keycloakLanguageCode){
                    this.setCurrentLanguageBasedOnCode(keycloakLanguageCode);
                    window.location.href = `/dcm4chee-arc/ui2/${keycloakLanguageCode}/`;
                }else{
                    this.setCurrentLanguageBasedOnCode(keycloakLanguageCode);
                }
            }
        }else{
            const currentSavedLanguageCode = localStorage.getItem('current_language');
            if(!currentSavedLanguageCode){
                this.setCurrentLanguageBasedOnCode("en");
                window.location.href = `/dcm4chee-arc/ui2/en/`;
            }else{
                if(this.mainservice.global && !this.mainservice.global.notSecure) {
                    const regex = /dcm4chee-arc\/ui2\/(\w{2})\//gm;
                    let match;
                    if ((match = regex.exec(location.href)) !== null) {
                        if(match[1] != currentSavedLanguageCode){
                            window.location.href = `/dcm4chee-arc/ui2/${currentSavedLanguageCode}/`;
                        }
                    }
                }
            }

        }
    }

    setCurrentLanguageBasedOnCode(languageCode:string){
        try{
            localStorage.setItem('current_language', languageCode || "en");
        }catch (e) {
        }
    }


}

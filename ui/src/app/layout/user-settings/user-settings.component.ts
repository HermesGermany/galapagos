import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { CertificateService } from '../../shared/services/certificates.service';
import { combineLatest, firstValueFrom, Observable, ReplaySubject } from 'rxjs';
import { EnvironmentsService, KafkaEnvironment } from 'src/app/shared/services/environments.service';
import { map, shareReplay } from 'rxjs/operators';
import { ToastService } from 'src/app/shared/modules/toast/toast.service';
import { TranslateService } from '@ngx-translate/core';
import { DateTime } from 'luxon';
import { ApiKeyService } from '../../shared/services/apikey.service';
import { withCurrentLanguage } from '../../shared/util/translate-util';
import { copy } from '../../shared/util/copy-util';

interface ExistingAuthenticationInfo {

    authenticationId?: string;

    expiresAt: string;

}

@Component({
    selector: 'app-user-settings',
    templateUrl: './user-settings.component.html',
    styleUrls: ['./user-settings.component.scss'],
    animations: [routerTransition()]
})
export class UserSettingsComponent implements OnInit {

    devCertsEnabledEnvironments: Observable<KafkaEnvironment[]>;

    devApiKeysEnabledEnvironments: Observable<KafkaEnvironment[]>;

    selectedEnvironment: KafkaEnvironment;

    existingCertificateMessage: Observable<string>;

    existingApiKeyMessage: Observable<string>;

    saveKeyWarning: Observable<string>;

    existingAuthenticationInfo = new ReplaySubject<ExistingAuthenticationInfo>(1);

    newApiKey: string;

    copiedKey: boolean;

    newApiSecret: string;

    copiedSecret: boolean;

    showApiKeyTable: boolean;

    authenticationMode: Observable<string>;

    constructor(private environmentsService: EnvironmentsService, private certificateService: CertificateService,
                private toasts: ToastService, private translate: TranslateService, private apiKeyService: ApiKeyService) {
    }

    ngOnInit() {
        this.authenticationMode = this.environmentsService.getCurrentEnvironment().pipe(map(env => env.authenticationMode));
        this.devCertsEnabledEnvironments = combineLatest(
            [this.certificateService.getEnvironmentsWithDevCertSupport(),
                this.environmentsService.getEnvironments()])
            .pipe(map(value => value[1].filter(env => value[0].indexOf(env.id) > -1)));

        this.devApiKeysEnabledEnvironments = combineLatest(
            [this.certificateService.getEnvironmentsWithDevApikeySupport(),
                this.environmentsService.getEnvironments()])
            .pipe(map(value => value[1].filter(env => value[0].indexOf(env.id) > -1)));

        const messageFn = (lang: string, messageKey: string, info: ExistingAuthenticationInfo) => {
            if (!info.expiresAt) {
                return null;
            }
            const expiresAt = DateTime.fromISO(info.expiresAt).setLocale(lang).toFormat('f');

            return this.translate.instant(messageKey, {
                expiresAt: expiresAt,
                apiKey: info.authenticationId
            });
        };

        this.existingCertificateMessage = withCurrentLanguage(this.translate, this.existingAuthenticationInfo,
            (lang, info) => messageFn(lang, 'EXISTING_DEVELOPER_CERTIFICATE_HTML', info)).pipe(shareReplay());

        this.existingApiKeyMessage = withCurrentLanguage(this.translate, this.existingAuthenticationInfo,
            (lang, info) => messageFn(lang, 'EXISTING_DEVELOPER_API_Key_HTML', info)).pipe(shareReplay());

        this.saveKeyWarning = withCurrentLanguage(this.translate, this.existingAuthenticationInfo,
            (lang, info) => messageFn(lang, 'SAVE_DEV_KEY_WARNING', info)).pipe(shareReplay());

        this.copiedKey = false;
        this.copiedSecret = false;
        this.showApiKeyTable = false;
    }

    updateExistingCertificateMessage() {
        this.existingAuthenticationInfo.next({ authenticationId: null, expiresAt: null });

        if (!this.selectedEnvironment) {
            return;
        }

        firstValueFrom(this.certificateService.getDeveloperAuthenticationInfo(this.selectedEnvironment.id)
        ).then(val => {
            this.existingAuthenticationInfo.next({
                authenticationId: val.authentications[this.selectedEnvironment.id].authentication.dn,
                expiresAt: val.authentications[this.selectedEnvironment.id].authentication.expiresAt
            });
        }
        ).catch(err => {
            this.toasts.addHttpErrorToast('DEVELOPER_CERTIFICATE_INFO_ERROR', err);
        });
    }

    async generateCertificate() {
        const successMsg = await firstValueFrom(this.translate.get('MSG_DEVELOPER_CERTIFICATE_SUCCESS'));
        const errorMsg = await firstValueFrom(this.translate.get('MSG_DEVELOPER_CERTIFICATE_ERROR'));

        this.certificateService.downloadDeveloperCertificate(this.selectedEnvironment.id).then(
            () => this.toasts.addSuccessToast(successMsg),
            err => this.toasts.addHttpErrorToast(errorMsg, err)
        )
            .then(() => this.updateExistingCertificateMessage());
    }

    async generateApikey(): Promise<any> {
        const successMsg = await firstValueFrom(this.translate.get('MSG_DEVELOPER_API_KEY_SUCCESS'));
        const errorMsg = await firstValueFrom(this.translate.get('MSG_DEVELOPER_API_KEY_ERROR'));

        return this.apiKeyService.createDeveloperApiKey(this.selectedEnvironment.id).then(
            val => {
                this.newApiKey = val.key;
                this.newApiSecret = val.secret;
                this.toasts.addSuccessToast(successMsg);
                this.showApiKeyTable = true;
                this.existingApiKeyMessage = null;
            },
            err => this.toasts.addHttpErrorToast(errorMsg, err)
        ).then(() => this.updateExistingApiKeyMessage(false));
    }

    //DELETE JUST FOR TESTING
    testToast() {
        this.toasts.addSuccessToast("TEST MESSAGE#");
    }

    updateExistingApiKeyMessage(hideTable: boolean) {
        this.existingAuthenticationInfo.next({ expiresAt: null });

        if (!this.selectedEnvironment) {
            return;
        }

        firstValueFrom(this.certificateService.getDeveloperAuthenticationInfo(this.selectedEnvironment.id)
        ).then(val => {
            if (val.authentications[this.selectedEnvironment.id]) {
                this.existingAuthenticationInfo.next({
                    expiresAt: val.authentications[this.selectedEnvironment.id].authentication.expiresAt,
                    authenticationId: val.authentications[this.selectedEnvironment.id].authentication.apiKey
                });
                if (hideTable) {
                    this.showApiKeyTable = false;
                }
            }
        }).catch(err => {
            this.toasts.addHttpErrorToast('DEVELOPER_API_KEY_INFO_ERROR', err);
        });
    }


    copyValue(value: string) {
        copy(value);
        if (value === this.newApiKey) {
            this.copiedKey = true;
            this.copiedSecret = false;
        } else {
            this.copiedKey = false;
            this.copiedSecret = true;
        }
    }

}

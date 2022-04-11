import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { AuthenticationResponse, CertificateService } from '../../shared/services/certificates.service';
import { combineLatest, concat, Observable, of, Subject } from 'rxjs';
import { EnvironmentsService, KafkaEnvironment } from 'src/app/shared/services/environments.service';
import { flatMap, map, shareReplay, take } from 'rxjs/operators';
import { ToastService } from 'src/app/shared/modules/toast/toast.service';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import 'moment/min/locales';

@Component({
    selector: 'app-user-settings',
    templateUrl: './user-settings.component.html',
    styleUrls: ['./user-settings.component.scss'],
    animations: [routerTransition()]
})
export class UserSettingsComponent implements OnInit {

    devCertsEnabledEnvironments: Observable<KafkaEnvironment[]>;

    devApikeysEnabledEnvironments: Observable<KafkaEnvironment[]>;

    selectedEnvironment: KafkaEnvironment;

    existingCertificateMessage: Observable<string>;

    existingApiKeyMessage: Observable<string>;

    existingCertificateInfo = new Subject<AuthenticationResponse>();

    existingApikeyInfo = new Subject<AuthenticationResponse>();

    newApiKey: string;

    copiedKey: boolean;

    newApiSecret: string;

    copiedSecret: boolean;

    showApiKeyTable: boolean;

    authenticationMode: Observable<string>;

    constructor(private environmentsService: EnvironmentsService, private certificateService: CertificateService,
                private toasts: ToastService, private translate: TranslateService) {
    }

    ngOnInit() {
        this.authenticationMode = this.environmentsService.getCurrentEnvironment().pipe(map(env => env.authenticationMode));
        this.devCertsEnabledEnvironments = combineLatest(
            [this.certificateService.getEnvironmentsWithDevCertSupport(),
                this.environmentsService.getEnvironments()])
            .pipe(map(value => value[1].filter(env => value[0].indexOf(env.id) > -1)));

        this.devApikeysEnabledEnvironments = combineLatest(
            [this.certificateService.getEnvironmentsWithDevApikeySupport(),
                this.environmentsService.getEnvironments()])
            .pipe(map(value => value[1].filter(env => value[0].indexOf(env.id) > -1)));

        // wrap ngx-translate service EventEmitter into a useful replay observable
        const lang = concat(of(this.translate.currentLang), this.translate.onLangChange
            .pipe(map(event => event.lang))).pipe(shareReplay(1));

        this.existingCertificateMessage = combineLatest([lang, this.existingCertificateInfo]).pipe(
            flatMap(values => {
                if (!values[1].dn) {
                    return of(null);
                }
                // @ts-ignore
                const expiresAt = moment(values[1].expiresAt).locale(values[0]).format('L LT');
                return this.translate.get('EXISTING_DEVELOPER_CERTIFICATE_HTML', { expiresAt: expiresAt })
                    .pipe(map(o => o as string));
            })).pipe(shareReplay());

        this.existingApiKeyMessage = combineLatest([lang, this.existingApikeyInfo]).pipe(
            flatMap(values => {
                // @ts-ignore
                const expiresAt = moment(values[1].expiresAt).locale(values[0]).format('L LT');
                return this.translate.get('EXISTING_DEVELOPER_API_Key_HTML', { expiresAt: expiresAt })
                    .pipe(map(o => o as string));
            })).pipe(shareReplay());

        this.copiedKey = false;
        this.copiedSecret = false;
        this.showApiKeyTable = false;
    }

    updateExistingCertificateMessage() {
        this.existingCertificateInfo.next({ dn: null, expiresAt: null });

        if (!this.selectedEnvironment) {
            return;
        }

        this.certificateService.getDeveloperAuthenticationInfo(this.selectedEnvironment.id)
            .pipe(take(1)).toPromise().then(val => {
                this.existingCertificateInfo.next({
                    dn: val.authentications[this.selectedEnvironment.id].authentication.dn,
                    expiresAt: val.authentications[this.selectedEnvironment.id].authentication.expiresAt
                });
            }
            ).catch(err => {
                this.toasts.addHttpErrorToast('DEVELOPER_CERTIFICATE_INFO_ERROR', err);
            });
    }

    async generateCertificate() {
        const successMsg = await this.translate.get('MSG_DEVELOPER_CERTIFICATE_SUCCESS').pipe(take(1)).toPromise();
        const errorMsg = await this.translate.get('MSG_DEVELOPER_CERTIFICATE_ERROR').pipe(take(1)).toPromise();

        this.certificateService.downloadDeveloperCertificate(this.selectedEnvironment.id).then(
            () =>  this.toasts.addSuccessToast(successMsg),
            err => this.toasts.addHttpErrorToast(errorMsg, err)
        )
            .then(() => this.updateExistingCertificateMessage());
    }

    async generateApikey(): Promise<any> {
        const successMsg = await this.translate.get('MSG_DEVELOPER_API_KEY_SUCCESS').pipe(take(1)).toPromise();
        const errorMsg = await this.translate.get('MSG_DEVELOPER_API_KEY_ERROR').pipe(take(1)).toPromise();

        return this.certificateService.downloadDeveloperApiKey(this.selectedEnvironment.id).then(
            val => {
                this.newApiKey = val.key;
                this.newApiSecret = val.secret;
                this.toasts.addSuccessToast(successMsg);
                this.showApiKeyTable = true;
                this.existingApiKeyMessage = null;
            },
            err => this.toasts.addHttpErrorToast(errorMsg, err)
        ).then(() => this.updateExistingApiKeyMessage());
    }

    updateExistingApiKeyMessage() {
        this.existingApikeyInfo.next({ expiresAt: null });

        if (!this.selectedEnvironment) {
            return;
        }

        this.certificateService.getDeveloperAuthenticationInfo(this.selectedEnvironment.id)
            .pipe(take(1)).toPromise().then(val => {
                this.existingApikeyInfo.next({
                    expiresAt: val.authentications[this.selectedEnvironment.id].authentication.expiresAt
                });
            }).catch(err => {
                this.toasts.addHttpErrorToast('DEVELOPER_API_KEY_INFO_ERROR', err);
            });
    }

    copyValue(value: string) {
        const selBox = document.createElement('textarea');
        selBox.style.position = 'fixed';
        selBox.style.left = '0';
        selBox.style.top = '0';
        selBox.style.opacity = '0';
        selBox.value = value;
        document.body.appendChild(selBox);
        selBox.focus();
        selBox.select();
        document.execCommand('copy');
        document.body.removeChild(selBox);

        if (value === this.newApiKey) {
            this.copiedKey = true;
            this.copiedSecret = false;
        } else {
            this.copiedKey = false;
            this.copiedSecret = true;
        }
    }
}

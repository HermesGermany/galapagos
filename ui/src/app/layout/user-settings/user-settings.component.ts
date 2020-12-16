import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { CertificateService, DeveloperCertificateInfo } from '../../shared/services/certificates.service';
import { Observable, combineLatest, Subject, of, concat } from 'rxjs';
import { EnvironmentsService, KafkaEnvironment } from 'src/app/shared/services/environments.service';
import { map, take, shareReplay, flatMap } from 'rxjs/operators';
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

    selectedEnvironment: KafkaEnvironment;

    existingCertificateMessage: Observable<string>;

    existingCertificateInfo = new Subject<DeveloperCertificateInfo>();

    constructor(private environmentsService: EnvironmentsService, private certificateService: CertificateService,
        private toasts: ToastService, private translate: TranslateService) {
    }

    ngOnInit() {
        this.devCertsEnabledEnvironments = combineLatest(
            [this.certificateService.getEnvironmentsWithDevCertSupport(),
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
                const expiresAt = moment(values[1].expiresAt).locale(values[0]).format('L LT');
                return this.translate.get('EXISTING_DEVELOPER_CERTIFICATE_HTML', { expiresAt: expiresAt })
                    .pipe(map(o => <string>o));
        })).pipe(shareReplay());
    }

    updateExistingCertificateMessage() {
        this.existingCertificateInfo.next({ dn: null, expiresAt: null });

        if (!this.selectedEnvironment) {
            return;
        }

        this.certificateService.getDeveloperCertificateInfo(this.selectedEnvironment.id)
            .pipe(take(1)).toPromise().then(val => this.existingCertificateInfo.next(val),
            err => { });
    }

    async generateCertificate() {
        const successMsg = await this.translate.get('MSG_DEVELOPER_CERTIFICATE_SUCCESS').pipe(take(1)).toPromise();
        const errorMsg = await  this.translate.get('MSG_DEVELOPER_CERTIFICATE_ERROR').pipe(take(1)).toPromise();

        this.certificateService.downloadDeveloperCertificate(this.selectedEnvironment.id).then(
            () => this.toasts.addSuccessToast(successMsg),
            err => this.toasts.addHttpErrorToast(errorMsg, err)
        )
        .then(() => this.updateExistingCertificateMessage());
    }
}

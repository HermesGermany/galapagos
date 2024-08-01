import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';

import { DashboardComponent } from './dashboard.component';
import { EnvironmentsService } from '../../shared/services/environments.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { ApplicationsService } from '../../shared/services/applications.service';
import { ServerInfoService } from '../../shared/services/serverinfo.service';
import { Location } from '@angular/common';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { firstValueFrom, of } from 'rxjs';
import { PageHeaderModule } from '../../shared';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { LanguageTranslationModule } from '../../shared/modules/language-translation/language-translation.module';
import { AuthService } from '../../shared/services/auth.service';
import { MockAuthService } from '../../shared/util/test-util';

describe('DashboardComponent', () => {
    let component: DashboardComponent;
    let fixture: ComponentFixture<DashboardComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot(),
                LanguageTranslationModule,
                NgbModule,
                HttpClientTestingModule,
                RouterTestingModule,
                BrowserAnimationsModule,
                PageHeaderModule
            ],
            declarations: [DashboardComponent],
            providers: [
                { provide: AuthService, useClass: MockAuthService },
                EnvironmentsService,
                ToastService,
                ApplicationsService,
                ServerInfoService,
                Location,
                TranslateService
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(DashboardComponent);
        component = fixture.componentInstance;
    });


    it('should create Dashboard Component', () => {
        expect(component).toBeTruthy();
    });

    it('should display correct kafka version', () => {
        const serverInfoService = fixture.debugElement.injector.get(ServerInfoService);
        const spy: jasmine.Spy = spyOn(serverInfoService, 'getKafkaVersion').and.returnValue(of('1.0'));
        const debugElement = fixture.debugElement;
        component.selectedEnvironment = of({
            id: 'devtest',
            name: 'dev stage',
            bootstrapServers: 'someServer',
            production: false,
            stagingOnly: false,
            authenticationMode: 'certificates'

        });

        fixture.detectChanges();
        expect(spy).toHaveBeenCalled();
        expect(spy.calls.all().length).toBe(1);
        expect(debugElement.query(By.css('#kafkaVersion')).nativeElement.innerText).toEqual('1.0');
    });

    it('should display correct bootstrap server', fakeAsync(() => {
        const environmentsService = fixture.debugElement.injector.get(EnvironmentsService);
        const spy: jasmine.Spy = spyOn(environmentsService, 'getCurrentEnvironment').and.returnValue(of({
            id: 'devtest',
            name: 'dev stage',
            bootstrapServers: 'someServer',
            production: false,
            stagingOnly: false,
            authenticationMode: 'certificates'
        }));

        const debugElement = fixture.debugElement;
        TestBed.createComponent(DashboardComponent);
        expect(spy).toHaveBeenCalled();
        expect(spy.calls.all().length).toBe(1);
        firstValueFrom(component.selectedEnvironment).then(data => {
            expect(data.bootstrapServers).toBe('someServer');
            expect(debugElement.query(By.css('#bootstrapServers')).nativeElement.innerText).toEqual('someServer');
        });
    }));

    it('should display correct translation', (() => {
        const translateService = fixture.debugElement.injector.get(TranslateService);
        translateService.setTranslation('de', { 'Selected Kafka Environment': 'Ausgewählte Kafka-Umgebung' });
        translateService.use('de');
        fixture.detectChanges();
        const compiled = fixture.debugElement.nativeElement;

        expect(compiled.querySelector('label').textContent).toContain('Ausgewählte Kafka-Umgebung');

        translateService.use('en');
        fixture.detectChanges();

        expect(compiled.querySelector('label').textContent).toContain('Selected Kafka Environment');
    }));
});

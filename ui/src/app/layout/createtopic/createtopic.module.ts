import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CreateTopicRoutingModule } from './createtopic-routing.module';
import { CreateTopicComponent } from './createtopic.component';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbAlertModule, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { SpinnerWhileModule } from 'src/app/shared/modules/spinner-while/spinner-while.module';
import { DataSettingsComponent } from './datasettings/data-settings.component';
import { NgxSliderModule } from '@angular-slider/ngx-slider';

@NgModule({
    imports: [CommonModule, CreateTopicRoutingModule, TranslateModule, FormsModule, NgbAlertModule, SpinnerWhileModule, NgbModule,
        NgxSliderModule],
    declarations: [CreateTopicComponent, DataSettingsComponent]
})
export class CreateTopicModule {
}

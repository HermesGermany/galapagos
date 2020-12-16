import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbAlertModule } from '@ng-bootstrap/ng-bootstrap';
import { SpinnerWhileModule } from 'src/app/shared/modules/spinner-while/spinner-while.module';
import { UserSettingsRoutingModule } from './user-settings-routing.module';
import { UserSettingsComponent } from './user-settings.component';

@NgModule({
    imports: [CommonModule, UserSettingsRoutingModule, TranslateModule, FormsModule, NgbAlertModule, SpinnerWhileModule],
    declarations: [UserSettingsComponent]
})
export class UserSettingsModule {}

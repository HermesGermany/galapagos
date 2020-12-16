import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbAlertModule } from '@ng-bootstrap/ng-bootstrap';
import { SpinnerWhileModule } from 'src/app/shared/modules/spinner-while/spinner-while.module';
import { StagingComponent } from './staging.component';
import { StagingRoutingModule } from './staging-routing.module';

@NgModule({
    imports: [CommonModule, StagingRoutingModule, TranslateModule, FormsModule, NgbAlertModule, SpinnerWhileModule],
    declarations: [StagingComponent]
})
export class StagingModule {}

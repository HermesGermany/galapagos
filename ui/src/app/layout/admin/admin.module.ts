import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { AdminRoutingModule } from './admin-routing.module';
import { AdminComponent } from './admin.component';
import { TranslateModule } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { SpinnerWhileModule } from 'src/app/shared/modules/spinner-while/spinner-while.module';
import { AppSortableHeaderDirective } from './sortable.directive';

@NgModule({
    imports: [CommonModule, AdminRoutingModule, TranslateModule, FormsModule, NgbModule, SpinnerWhileModule],
    declarations: [AdminComponent, AppSortableHeaderDirective]
})
export class AdminModule {}

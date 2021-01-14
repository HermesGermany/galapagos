// Code from
// https://ng-bootstrap.github.io/#/components/table/examples#complete
import {Directive, EventEmitter, Input, Output, HostListener, HostBinding} from '@angular/core';

export type SortDirection = 'asc' | 'desc' | '';
const rotate: {[key: string]: SortDirection} = { asc: 'desc', desc: '', '': 'asc' };

export interface SortEvent {
  column: string;
  direction: SortDirection;
}

/* eslint-disable @angular-eslint/directive-selector */
@Directive({
  selector: 'th[sortable]'
})
/* eslint-enable @angular-eslint/directive-selector */
export class TableSortDirective {

  @Input() sortable: string;
  @Input() direction: SortDirection = '';
  @Output() sort = new EventEmitter<SortEvent>();

  @HostBinding('class.asc') ascClass = false;

  @HostBinding('class.desc') descClass = false;

  @HostListener('click')
  rotate() {
    this.direction = rotate[this.direction];
    this.ascClass = this.direction === 'asc';
    this.descClass = this.direction === 'desc';
    this.sort.emit({column: this.sortable, direction: this.direction});
  }
}

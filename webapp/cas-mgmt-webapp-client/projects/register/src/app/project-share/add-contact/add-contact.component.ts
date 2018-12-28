import {Component, OnInit} from '@angular/core';
import {MatAutocompleteSelectedEvent, MatDialogRef} from '@angular/material';
import {debounceTime, distinctUntilChanged, finalize, switchMap} from 'rxjs/operators';
import {Observable, Subject} from 'rxjs';
import {DefaultRegisteredServiceContact, UserService, SpinnerService} from 'mgmt-lib';

@Component({
  selector: 'app-add-contact',
  templateUrl: './add-contact.component.html'
})

export class AddContactComponent implements OnInit {

  private lookupContact = new Subject<string>();

  foundContacts: DefaultRegisteredServiceContact[];
  selectedContact: DefaultRegisteredServiceContact[] = [new DefaultRegisteredServiceContact()];
  i: number;

  constructor(public dialogRef: MatDialogRef<AddContactComponent>,
              public user: UserService,
              public spinner: SpinnerService) { }

  ngOnInit() {
      this.selectedContact[0].name = '';
      this.spinner.start('Looking up contact');
      this.lookupContact.pipe(
          debounceTime(500),
          distinctUntilChanged(),
          switchMap((query: string) => {
              if (query && query !== '' && query.length >= 3) {
                  return this.user.lookupContact(query);
              } else {
                  return Observable.create((observer) => observer.next([]));
              }
          }),
          finalize(() => this.spinner.stop())
      ).subscribe((resp: DefaultRegisteredServiceContact[])  => {
              this.foundContacts = resp;
          }
      );
  }

  doLookupContact(val: string, i: number) {
    this.i = i;
    this.lookupContact.next(val);
  }

  selection(sel: MatAutocompleteSelectedEvent ) {
    this.selectedContact[this.i] = sel.option.value;
  }

  addContact() {
    const nc = new DefaultRegisteredServiceContact();
    nc.name = '';
    this.selectedContact.push(nc);
  }

  removeContact(i: number) {
    this.selectedContact.splice(i, 1);
  }
}
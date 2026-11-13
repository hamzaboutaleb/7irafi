import { Component } from '@angular/core';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-my-jobs-loading',
  imports: [TranslatePipe],
  templateUrl: './my-jobs-loading.html',
  styleUrl: './my-jobs-loading.css',
})
export class MyJobsLoading {}

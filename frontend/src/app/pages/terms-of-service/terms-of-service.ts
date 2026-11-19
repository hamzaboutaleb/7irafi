import { Component } from '@angular/core';

import { Footer } from '../../components/footer/footer.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-terms-of-service',
  imports: [Navbar, Footer, TranslatePipe],
  templateUrl: './terms-of-service.html',
  styleUrl: './terms-of-service.css',
})
export class TermsOfService {}

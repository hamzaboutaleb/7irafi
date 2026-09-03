import { Component } from '@angular/core';

import { Footer } from '../../components/footer/footer.js';
import { Navbar } from '../../components/navbar/navbar.js';

@Component({
  selector: 'app-terms-of-service',
  imports: [Navbar, Footer],
  templateUrl: './terms-of-service.html',
  styleUrl: './terms-of-service.css',
})
export class TermsOfService {}

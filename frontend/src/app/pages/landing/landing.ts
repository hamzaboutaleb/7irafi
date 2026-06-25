import { Component } from '@angular/core';
import { Navbar } from "../../components/navbar/navbar";
import { Hero } from "../../components/hero/hero";
import { Categories } from "../../components/categories/categories";

@Component({
  selector: 'app-landing',
  imports: [Navbar, Hero, Categories],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class Landing {}

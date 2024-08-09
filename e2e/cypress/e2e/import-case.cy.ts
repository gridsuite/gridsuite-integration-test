


describe('Import a case', () => {
    before(function () {
        const gridExploreUrl = 'http://localhost:3000/';
        // runs once before all tests in the block
        cy.loginToGridsuite('jamal', 'password', gridExploreUrl);
        cy.visit(
            gridExploreUrl
        );

        //create a loop to wait for the page to load
        while (cy.get('button').contains('Connexion').should('not.exist')) {
            cy.wait(1000);  
        }

        console.log('Page loaded');
        fetch('http://localhost:3000/api/studies')
      
    });

    it('should create a generator modification', () => {

    });

});
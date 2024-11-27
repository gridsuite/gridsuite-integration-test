describe('template spec', () => {

  before(function () {
    const gridExploreUrl = 'http://localhost:3000/';
    // runs once before all tests in the block
    cy.loginToGridsuite('jamal', 'password', gridExploreUrl);
        cy.visit(
            gridExploreUrl
        );

    //create a loop to wait for the page to load
    //while (cy.get('button').contains('Connexion').should('not.exist')) {
        cy.wait(500);  
    //}
  
});

  // it('create a study', () => {
  //   // cy.get('button').contains('Connexion').should('exist')
  //   cy.get('[aria-label="tests-cy"]').click();
  //   //cy.contains('Dossier vide').should('exist')
  //   //click right on the empty folder
  //   cy.get('.ag-header-row').rightclick();
  //   //click on the create study button
  //   cy.contains('Créer une étude').click();
  //   cy.get('input[name=studyName]').type('newStudy');
  //   cy.get('input[type=file]').attachFile("data-files/MicroGridBE.xiidm");
    
  //   cy.get('button').contains('Valider').click();
  // })


  // //CGMES_v2.4.15_RealGridTestConfiguration_v2.zip
  // it('create a cgmes study', () => {
  //   // cy.get('button').contains('Connexion').should('exist')
  //   cy.get('[aria-label="tests-cy"]').click();
  //   cy.get('.ag-header-row').rightclick();
  //   //click on the create study button
  //   cy.contains('Créer une étude').click();
  //   cy.get('input[name=studyName]').type('RealGridTest');
  //   cy.get('input[type=file]').attachFile("data-files/CGMES_v2.4.15_RealGridTestConfiguration_v2.zip");
    
  //   cy.get('button').contains('Valider').click();
  // })

  it('duplicate studies', () => {
    // cy.get('button').contains('Connexion').should('exist')
    cy.get('[aria-label="tests-cy"]').click();
    const nb = 20;
    for (let i = 0; i < nb; i++) {
      cy.contains('RealGridTest').rightclick();
      cy.contains('Dupliquer').click({  multiple: true, force: true });
    }

  })


})
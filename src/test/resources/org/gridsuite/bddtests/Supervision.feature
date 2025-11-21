@tagSupervision
Feature: GridSuite supervision and monitoring tests

  Background:
    Given using platform "local"

  # ---------------------------------------------------------------------------
  Rule: Check the global health of the system

    Scenario: create a new study from a new case, run 2 loadflow computations

      Given using tmp directory as "tmpdir"

      When create case "microGrid" in "tmpdir" from resource "data/MicroGrid_NL.xiidm"
      And create study "microStudy" in "tmpdir" from case "microGrid"
      And get study "microStudy" from "tmpdir"
      And get first root network from "microStudy"
      And get node "N1"
      And set loadflow parameters with resource "data/defaultLfParamsWithNoCountry.json" with provider "OpenLoadFlow"
      And using loadflow "OpenLoadFlow"
      And run loadflow
      Then loadflow status is "CONVERGED"

      When open switch "br7"
      Then loadflow status is "NOT_DONE"

      When run loadflow
      Then loadflow status is "CONVERGED"





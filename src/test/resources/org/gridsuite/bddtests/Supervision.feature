@tagSupervision
Feature: GridSuite supervision and monitoring tests

  Background:
    Given using platform "local"

  # ---------------------------------------------------------------------------
  Rule: Check the global health of the system
      
    Scenario: create a new study from a new case, run 2 loadflow computations

      Given using tmp root directory as "tmpdir"

      When create case "microGrid" in "tmpdir" from resource "data/MicroGrid_NL.xiidm"
      Then wait for "microGrid" case creation in "tmpdir"

      When create study "microStudy" in "tmpdir" from case "microGrid"
      Then wait for "microStudy" study creation in "tmpdir"

      When get study "microStudy" from "tmpdir"
      And get node "modification node 0"
      And using loadflow "OpenLoadFlow"
      And run loadflow
      Then wait for loadflow status "CONVERGED"

      When open switch "br7"
      Then wait for loadflow status "NOT_DONE"

      When run loadflow
      Then wait for loadflow status "CONVERGED"





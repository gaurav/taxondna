#!/usr/bin/perl -w

=head1 NAME

hang-during-split.t

=head2 GOALS

We have a case where SM hangs while loading a file.
Let's test that?

=cut

use Test::More;

plan(tests => 1);

sub exec_sequence_matrix {
    system("/usr/bin/java",
        "-jar" => "../Release/SequenceMatrix.jar",
        @_
    );
}

exec_sequence_matrix(
    "--add" => "files/primates.nex", 
);

pass("Added a Nexus file.");
